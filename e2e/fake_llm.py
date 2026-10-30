"""OpenAI compatible stub used to test the agent -> MCP -> services chain without an API key.

It decides nothing intelligently : it asks for one tool, then repeats the tool result. It proves the wiring only.
Usage : python e2e/fake_llm.py   then start chatbot-service with LLM_BASE_URL=http://host.docker.internal:9999
"""
import json
from http.server import BaseHTTPRequestHandler, HTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        request = json.loads(self.read_body())
        tools = [tool["function"]["name"] for tool in request.get("tools", [])]
        print("Tools offered by the agent :", tools, flush=True)
        last = request["messages"][-1]
        if last["role"] == "tool":
            message = {"role": "assistant", "content": "[LLM simule] Resultat de l'outil : " + str(last["content"])[:400]}
            finish = "stop"
        else:
            message = {"role": "assistant", "content": None, "tool_calls": [{
                "id": "call_1", "type": "function", "function": {"name": "listAccounts", "arguments": "{}"}}]}
            finish = "tool_calls"
        body = json.dumps({"id": "fake", "object": "chat.completion", "created": 0, "model": request["model"],
                           "choices": [{"index": 0, "message": message, "finish_reason": finish}],
                           "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def read_body(self):
        if self.headers["Content-Length"]:
            return self.rfile.read(int(self.headers["Content-Length"]))
        body = b""  # chunked request
        while (size := int(self.rfile.readline().strip(), 16)) > 0:
            body += self.rfile.read(size)
            self.rfile.readline()
        self.rfile.readline()
        return body

    def log_message(self, *args):
        pass


HTTPServer(("0.0.0.0", 9999), Handler).serve_forever()
