// Teste de carga da API (Etapa 6). Uso (API em http://localhost:8080 e Keycloak em :8180, veja o README):
//   docker run --rm -i -e API=http://host.docker.internal:8080 -e KEYCLOAK=http://host.docker.internal:8180 \
//     -v "$PWD/k6:/k6" grafana/k6:1.8.2 run /k6/carga.js
// Usa os usuários de DESENVOLVIMENTO do realm importado (keycloak/realm-reservas.json).
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const API = __ENV.API || 'http://localhost:8080';
const KEYCLOAK = __ENV.KEYCLOAK || 'http://localhost:8180';
const SALAS = 20;
const TAXA = Number(__ENV.TAXA || 100); // requisições (iterações) por segundo
// limites de p95 em ms (sobrescrevíveis, para provar que o threshold derruba o teste)
const LIMITE_LISTAR = Number(__ENV.LIMITE_LISTAR || 100);
const LIMITE_RESERVAR = Number(__ENV.LIMITE_RESERVAR || 200);

export const options = {
  scenarios: {
    // taxa de chegada constante: a carga NÃO diminui quando a API fica lenta (como acontece com usuários de verdade)
    mista: {
      executor: 'constant-arrival-rate',
      rate: TAXA,
      timeUnit: '1s',
      duration: __ENV.DURACAO || '60s',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    // o limite que vale: se estourar, o k6 termina com código de saída diferente de 0 (e o CI falharia)
    http_req_failed: ['rate<0.01'],                       // menos de 1% de erros
    'http_req_duration{operacao:listar}': [`p(95)<${LIMITE_LISTAR}`],  // listagem (vem do cache)
    'http_req_duration{operacao:reservar}': [`p(95)<${LIMITE_RESERVAR}`], // reserva (transação com lock e constraint)
    checks: ['rate>0.99'],
  },
};

function token(usuario, senha) {
  const r = http.post(`${KEYCLOAK}/realms/reservas/protocol/openid-connect/token`,
    { grant_type: 'password', client_id: 'reservas-cli', username: usuario, password: senha });
  return r.json('access_token');
}

export function setup() {
  const admin = token('admin', 'admin-dev');
  const ana = token('ana', 'ana-dev');
  const cabecalho = { headers: { Authorization: `Bearer ${admin}`, 'Content-Type': 'application/json' } };
  const salas = [];
  for (let i = 0; i < SALAS; i++) {
    const r = http.post(`${API}/salas`, JSON.stringify({ nome: `Sala k6 ${Date.now()}-${i}`, capacidade: 10 }), cabecalho);
    salas.push(r.json('id'));
  }
  return { ana, salas, base: Date.now() + 60 * 24 * 3600 * 1000 };
}

export default function (dados) {
  const autorizacao = { Authorization: `Bearer ${dados.ana}`, 'Content-Type': 'application/json' };
  const n = exec.scenario.iterationInTest;
  if (n % 5 === 0) {
    // 20%: reserva de um horário único (sala e hora diferentes a cada iteração, então não há conflito esperado)
    const sala = dados.salas[n % SALAS];
    const hora = Math.floor(n / SALAS);
    const inicio = new Date(dados.base + hora * 3600 * 1000);
    const fim = new Date(inicio.getTime() + 3600 * 1000);
    const r = http.post(`${API}/reservas`,
      JSON.stringify({ salaId: sala, inicio: inicio.toISOString(), fim: fim.toISOString() }),
      { headers: autorizacao, tags: { operacao: 'reservar' } });
    check(r, { 'reserva criada (201)': (x) => x.status === 201 });
  } else {
    // 80%: listagem de salas
    const r = http.get(`${API}/salas`, { headers: autorizacao, tags: { operacao: 'listar' } });
    check(r, { 'listagem (200)': (x) => x.status === 200 });
  }
}
