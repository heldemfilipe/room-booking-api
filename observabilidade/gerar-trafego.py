"""Gera tráfego de teste na API local para alimentar o dashboard (Etapa 5). Só biblioteca padrão do Python.

Uso (com `docker compose up -d` e a API rodando):
    python observabilidade/gerar-trafego.py normal 120      # 120 s de tráfego saudável
    python observabilidade/gerar-trafego.py lento 120       # idem, mas cada ciclo chama o endpoint com pg_sleep de 2 s (exige APP_DIAGNOSTICO=true)

Os usuários e senhas são os de DESENVOLVIMENTO do realm importado (keycloak/realm-reservas.json).
"""
import json
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

API = "http://localhost:8080"
TOKEN_URL = "http://localhost:8180/realms/reservas/protocol/openid-connect/token"


def token(usuario, senha):
    corpo = urllib.parse.urlencode(
        {"grant_type": "password", "client_id": "reservas-cli", "username": usuario, "password": senha}).encode()
    with urllib.request.urlopen(urllib.request.Request(TOKEN_URL, corpo)) as r:
        return json.load(r)["access_token"]


def chamar(metodo, caminho, tk, corpo=None):
    dados = json.dumps(corpo).encode() if corpo is not None else None
    req = urllib.request.Request(API + caminho, dados, method=metodo, headers={
        "Authorization": "Bearer " + tk, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.load(r) if r.length != 0 else None
    except urllib.error.HTTPError as e:
        return e.code, None


def main():
    modo = sys.argv[1] if len(sys.argv) > 1 else "normal"
    duracao = int(sys.argv[2]) if len(sys.argv) > 2 else 60
    # os tokens do Keycloak duram 5 minutos: renova a cada 60 s para o tráfego longo não virar 401
    tokens = {}

    def renovar():
        tokens["admin"], tokens["ana"], tokens["bia"] = token("admin", "admin-dev"), token("ana", "ana-dev"), token("bia", "bia-dev")
        tokens["quando"] = time.time()

    renovar()
    admin = tokens["admin"]
    salas = []
    for i in range(3):
        _, sala = chamar("POST", "/salas", admin, {"nome": f"Sala {uuid.uuid4().hex[:6]}", "capacidade": 10 + i})
        salas.append(sala["id"])
    base = datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0) + timedelta(days=30)
    fim_em = time.time() + duracao
    contador = [0]

    def horario(h):
        return (base + timedelta(hours=h)).strftime("%Y-%m-%dT%H:%M:%SZ")

    def trabalhador(n):
        while time.time() < fim_em:
            if n == 0 and time.time() - tokens["quando"] > 60:
                renovar()
            admin, ana, bia = tokens["admin"], tokens["ana"], tokens["bia"]
            chamar("GET", "/salas", ana)
            sala = salas[contador[0] % len(salas)]
            h = contador[0] % 200
            contador[0] += 1
            # a mesma sala e o mesmo horário por dois usuários: um 201 e um 409
            chamar("POST", "/reservas", ana, {"salaId": sala, "inicio": horario(h), "fim": horario(h + 1)})
            chamar("POST", "/reservas", bia, {"salaId": sala, "inicio": horario(h), "fim": horario(h + 1)})
            if modo == "lento":
                chamar("GET", "/diagnostico/lento?segundos=2", admin)
            time.sleep(0.2)

    threads = [threading.Thread(target=trabalhador, args=(n,)) for n in range(4)]
    [t.start() for t in threads]
    [t.join() for t in threads]
    print(f"fim: {contador[0]} ciclos em modo '{modo}'")


if __name__ == "__main__":
    main()
