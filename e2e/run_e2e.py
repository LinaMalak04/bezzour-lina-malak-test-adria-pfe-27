"""End-to-end checks against the running Docker Compose stack. Prints a line per check and exits 1 on any failure.

Usage : python e2e/run_e2e.py   (GATEWAY_URL, ACCOUNT_URL, TRANSACTION_URL, KEYCLOAK_URL can override the defaults)
"""
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

GATEWAY = os.getenv("GATEWAY_URL", "http://localhost:18080")
ACCOUNT = os.getenv("ACCOUNT_URL", "http://localhost:8081")
TRANSACTION = os.getenv("TRANSACTION_URL", "http://localhost:8082")
KEYCLOAK = os.getenv("KEYCLOAK_URL", "http://localhost:8180")
ROOT = Path(__file__).resolve().parent.parent
CONFIG_FILE = ROOT / "config-service/src/main/resources/config-repo/application.properties"
A1 = "11111111-1111-1111-1111-111111111111"
A2 = "22222222-2222-2222-2222-222222222222"
A3 = "33333333-3333-3333-3333-333333333333"
failures = []


def call(method, url, body=None, headers=None):
    data = None if body is None else (body if isinstance(body, bytes) else json.dumps(body).encode())
    request = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json", **(headers or {})})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, response.read().decode(), dict(response.headers)
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode(), dict(error.headers)


def as_json(text):
    return json.loads(text) if text else None


def check(label, condition, detail=""):
    print(f"[{'OK' if condition else 'FAIL'}] {label}" + (f" -> {detail}" if detail else ""), flush=True)
    if not condition:
        failures.append(label)


def compose(*args):
    subprocess.run(["docker", "compose", *args], cwd=ROOT, check=True, capture_output=True)


def wait_healthy(url, attempts=60):
    for _ in range(attempts):
        try:
            if call("GET", url)[0] == 200:
                return True
        except OSError:
            pass
        time.sleep(2)
    return False


def balance(account_id):
    return as_json(call("GET", f"{GATEWAY}/api/accounts/{account_id}")[1])["balance"]


def pay(account_id, amount, currency="EUR", headers=None):
    return call("POST", f"{GATEWAY}/api/transactions", {"accountId": account_id, "amount": amount, "currency": currency,
                                                       "merchantCategory": "GROCERY", "country": "MA"}, headers)


def graphql(path, query):
    return as_json(call("POST", f"{GATEWAY}{path}", {"query": query})[1])


def sse_json(text):
    for line in text.splitlines():
        if line.startswith("data:"):
            return json.loads(line[5:])
    return json.loads(text)


def mcp_tools(base):
    headers = {"Accept": "application/json, text/event-stream"}
    _, _, response_headers = call("POST", f"{base}/mcp", {"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
        "protocolVersion": "2025-03-26", "capabilities": {}, "clientInfo": {"name": "e2e", "version": "1"}}}, headers)
    session = next(value for key, value in response_headers.items() if key.lower() == "mcp-session-id")
    headers["Mcp-Session-Id"] = session
    call("POST", f"{base}/mcp", {"jsonrpc": "2.0", "method": "notifications/initialized"}, headers)
    _, text, _ = call("POST", f"{base}/mcp", {"jsonrpc": "2.0", "id": 2, "method": "tools/list"}, headers)
    return sorted(tool["name"] for tool in sse_json(text)["result"]["tools"])


def token(username, password):
    form = urllib.parse.urlencode({"client_id": "bank-frontend", "grant_type": "password", "username": username, "password": password}).encode()
    request = urllib.request.Request(f"{KEYCLOAK}/realms/bank/protocol/openid-connect/token", data=form)
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read())["access_token"]


def test_rest_rules():
    print("\n--- Partie C : REST via la gateway et regles metier ---")
    status, text, _ = call("GET", f"{GATEWAY}/api/accounts")
    check("GET /api/accounts", status == 200 and len(as_json(text)) >= 3, f"{len(as_json(text))} comptes")

    before = balance(A1)
    status, text, _ = pay(A1, 250.00)
    transaction = as_json(text)
    check("Transaction valide de 250 EUR : 201 COMPLETED", status == 201 and transaction["status"] == "COMPLETED", f"id={transaction['id']}")
    check("Le solde est mis a jour", balance(A1) == before - 250, f"{before} -> {balance(A1)}")
    time.sleep(2)

    for label, response, expected in [
        ("Plafond : 1000.01 refuse", pay(A1, 1000.01), "AMOUNT_ABOVE_LIMIT"),
        ("Devise differente refusee", pay(A1, 10, "USD"), "CURRENCY_MISMATCH"),
    ]:
        check(label, response[0] == 422 and as_json(response[1])["code"] == expected, f"{response[0]} {as_json(response[1])['code']}")
    check("Montant negatif refuse", pay(A1, -5)[0] == 400, "400")
    check("Compte inexistant refuse", pay("99999999-9999-9999-9999-999999999999", 10)[0] == 404, "404")

    poor = as_json(call("POST", f"{GATEWAY}/api/accounts", {"customerReference": "CUST-E2E", "currency": "EUR", "initialBalance": 5})[1])["id"]
    response = pay(poor, 50)
    check("Solde insuffisant refuse", response[0] == 422 and as_json(response[1])["code"] == "INSUFFICIENT_BALANCE", as_json(response[1])["code"])
    call("PATCH", f"{GATEWAY}/api/accounts/{poor}/status", {"status": "BLOCKED"})
    response = pay(poor, 1)
    check("Compte bloque refuse", response[0] == 422 and as_json(response[1])["code"] == "ACCOUNT_NOT_ACTIVE", as_json(response[1])["code"])

    status, _, _ = call("POST", f"{GATEWAY}/internal/transfers", {})
    check("/internal n'est pas expose par la gateway", status in (401, 403, 404), str(status))


def test_transfer():
    print("\n--- Virement ---")
    source, target = balance(A1), balance(A2)
    status, text, _ = call("POST", f"{GATEWAY}/api/transactions/transfers", {"sourceAccountId": A1, "targetAccountId": A2, "amount": 100, "currency": "EUR"})
    legs = as_json(text)
    check("Virement de 100 EUR : 201, deux ecritures", status == 201 and len(legs) == 2, ", ".join(f"{leg['type']} {leg['status']}" for leg in legs))
    check("Soldes source et cible mis a jour", balance(A1) == source - 100 and balance(A2) == target + 100, f"source {source} -> {balance(A1)}, cible {target} -> {balance(A2)}")
    status, _, _ = call("POST", f"{GATEWAY}/api/transactions/transfers", {"sourceAccountId": A1, "targetAccountId": A3, "amount": 100, "currency": "EUR"})
    check("Virement entre devises differentes refuse", status == 422, str(status))


def test_graphql_and_mcp():
    print("\n--- Connecteurs GraphQL et MCP (meme couche service) ---")
    result = graphql("/accounts/graphql", "{ accounts { id customerReference status balance } }")
    check("GraphQL accounts", len(result["data"]["accounts"]) >= 3, json.dumps(result["data"]["accounts"][0]))
    result = graphql("/transactions/graphql", 'mutation { createTransaction(input: {accountId: "%s", amount: 12.5, currency: "EUR", merchantCategory: "FUEL", country: "MA"}) { id status amount } }' % A1)
    check("GraphQL createTransaction", result["data"]["createTransaction"]["status"] == "COMPLETED", json.dumps(result["data"]["createTransaction"]))
    result = graphql("/transactions/graphql", 'mutation { createTransaction(input: {accountId: "%s", amount: 5000, currency: "EUR", merchantCategory: "FUEL", country: "MA"}) { id } }' % A1)
    check("GraphQL applique les memes regles (plafond)", "ceiling" in result["errors"][0]["message"], result["errors"][0]["message"])
    tools = mcp_tools(ACCOUNT)
    check("MCP account-service : tools/list", "getAccount" in tools, ", ".join(tools))
    check("MCP account-service n'expose pas de debit direct", not any("debit" in tool.lower() for tool in tools))
    tools = mcp_tools(TRANSACTION)
    check("MCP transaction-service : tools/list", "transfer" in tools, ", ".join(tools))


def test_config_refresh():
    print("\n--- Partie D : plafond partage par config-service, modifiable a chaud ---")
    _, text, _ = call("GET", "http://localhost:8888/transaction-service/default")
    served = as_json(text)["propertySources"][0]["source"]["bank.transaction.max-amount"]
    check("config-service sert bank.transaction.max-amount", str(served) == "1000", str(served))
    original = CONFIG_FILE.read_bytes()
    try:
        CONFIG_FILE.write_bytes(original.replace(b"max-amount=1000", b"max-amount=2000"))
        call("POST", f"{TRANSACTION}/actuator/refresh")
        status, text, _ = pay(A1, 1500)
        check("Apres refresh a 2000 : 1500 accepte, sans redemarrage", status == 201, str(status))
    finally:
        CONFIG_FILE.write_bytes(original)
        call("POST", f"{TRANSACTION}/actuator/refresh")
    check("Valeur restauree : 1500 de nouveau refuse", pay(A1, 1500)[0] == 422, "422")


def test_kafka():
    print("\n--- Partie F : evenement Kafka journalise par le consumer ---")
    transaction = as_json(pay(A2, 33.33)[1])
    found = ""
    for _ in range(15):
        logs = subprocess.run(["docker", "compose", "logs", "transaction-audit-service"], cwd=ROOT, capture_output=True, text=True).stdout
        found = next((line for line in logs.splitlines() if transaction["id"] in line), "")
        if found:
            break
        time.sleep(1)
    check("Le consumer a recu l'evenement", bool(found), found[found.find("Event received"):] if found else "")
    check("La cle Kafka est accountId", f"key={A2}" in found)


def test_resilience():
    print("\n--- Partie C3 : panne d'account-service (Resilience4J) ---")
    compose("stop", "account-service")
    try:
        time.sleep(3)
        started = time.time()
        status, text, _ = call("POST", f"{TRANSACTION}/api/transactions", {"accountId": A1, "amount": 10, "currency": "EUR", "merchantCategory": "GROCERY", "country": "MA"})
        check("Service arrete : refus 503 (aucun faux solde)", status == 503, f"{status} en {time.time() - started:.1f}s")
        for _ in range(4):
            call("POST", f"{TRANSACTION}/api/transactions", {"accountId": A1, "amount": 10, "currency": "EUR", "merchantCategory": "GROCERY", "country": "MA"})
        _, text, _ = call("GET", f"{TRANSACTION}/actuator/circuitbreakers")
        state = as_json(text)["circuitBreakers"]["accountService"]["state"]
        check("Le circuit breaker s'ouvre", state == "OPEN", state)
        started = time.time()
        status, _, _ = call("POST", f"{TRANSACTION}/api/transactions", {"accountId": A1, "amount": 10, "currency": "EUR", "merchantCategory": "GROCERY", "country": "MA"})
        check("Circuit ouvert : echec immediat", status == 503 and time.time() - started < 1, f"{status} en {time.time() - started:.2f}s")
    finally:
        compose("start", "account-service")
    check("account-service redemarre", wait_healthy(f"{ACCOUNT}/actuator/health/readiness"))
    recovered = False
    for _ in range(30):
        status = call("GET", f"{TRANSACTION}/actuator/health")[0]
        status, _, _ = pay(A2, 1)
        if status == 201:
            recovered = True
            break
        time.sleep(5)
    check("Retour a la normale : transaction acceptee", recovered)


def test_security():
    print("\n--- Keycloak : JWT exige par la gateway (BANK_SECURITY_ENABLED=true) ---")
    subprocess.run(["docker", "compose", "up", "-d", "--wait", "gateway-service"], cwd=ROOT, check=True, capture_output=True,
                   env={**os.environ, "BANK_SECURITY_ENABLED": "true"})
    try:
        check("Sans jeton : 401", call("GET", f"{GATEWAY}/api/accounts")[0] == 401, "401")
        advisor = {"Authorization": f"Bearer {token('advisor', 'advisor-local-only')}"}
        customer = {"Authorization": f"Bearer {token('customer', 'customer-local-only')}"}
        for _ in range(20):
            if call("GET", f"{GATEWAY}/api/accounts", headers=advisor)[0] == 200:
                break
            time.sleep(3)
        check("Conseiller : lecture 200", call("GET", f"{GATEWAY}/api/accounts", headers=advisor)[0] == 200, "200")
        check("Conseiller : transaction 201", pay(A2, 1, headers=advisor)[0] == 201, "201")
        check("Client : lecture 200", call("GET", f"{GATEWAY}/api/accounts", headers=customer)[0] == 200, "200")
        check("Client : ecriture interdite 403", pay(A2, 1, headers=customer)[0] == 403, "403")
    finally:
        subprocess.run(["docker", "compose", "up", "-d", "--wait", "gateway-service"], cwd=ROOT, check=True, capture_output=True,
                       env={**os.environ, "BANK_SECURITY_ENABLED": "false"})
    for _ in range(20):
        if call("GET", f"{GATEWAY}/api/accounts")[0] == 200:
            break
        time.sleep(3)


def wait_for_discovery(attempts=30):
    # An unknown account answers 404 once transaction-service sees account-service in Eureka, 503 before that
    unknown = "99999999-9999-9999-9999-999999999999"
    for _ in range(attempts):
        try:
            if pay(unknown, 1)[0] == 404:
                return
        except OSError:
            pass
        time.sleep(3)
    raise SystemExit("The stack is not ready")


def main():
    wait_for_discovery()
    test_rest_rules()
    test_transfer()
    test_graphql_and_mcp()
    test_config_refresh()
    test_kafka()
    test_resilience()
    test_security()
    print(f"\n{'ECHECS : ' + ', '.join(failures) if failures else 'Tous les controles sont passes.'}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
