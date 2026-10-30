"""Scenario with the real LLM : needs chatbot-service started with GROQ_API_KEY.

Usage : python e2e/test_agent_llm.py   (GATEWAY_URL can override the default)
"""
import json
import os
import urllib.request

GATEWAY = os.getenv("GATEWAY_URL", "http://localhost:18080")
A1 = "11111111-1111-1111-1111-111111111111"
A2 = "22222222-2222-2222-2222-222222222222"
CONVERSATION = "scenario-llm-reel"


def post_chat(message):
    body = json.dumps({"conversationId": CONVERSATION, "message": message}).encode()
    request = urllib.request.Request(f"{GATEWAY}/api/chat", data=body, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.loads(response.read())["reply"]


def balance(account_id):
    with urllib.request.urlopen(f"{GATEWAY}/api/accounts/{account_id}", timeout=30) as response:
        return json.loads(response.read())["balance"]


def balances():
    return balance(A1), balance(A2)


def say(message):
    print(f"\nUtilisateur : {message}", flush=True)
    print(f"Agent : {post_chat(message)}", flush=True)
    print(f"Soldes CUST-001 / CUST-002 : {balances()}", flush=True)


def main():
    print(f"Soldes de depart CUST-001 / CUST-002 : {balances()}")
    say("Quel est le solde du compte de CUST-001 ?")
    say("Fais un virement de 100 EUR de CUST-001 vers CUST-002.")
    say("Oui, je confirme le virement.")
    say("Enregistre un paiement de 5000 EUR sur le compte de CUST-001, categorie TRAVEL, pays FR. Je confirme deja.")
    say("Enregistre un paiement de 1500 EUR sur le compte de CUST-001, categorie TRAVEL, pays FR. Je confirme deja.")


if __name__ == "__main__":
    main()
