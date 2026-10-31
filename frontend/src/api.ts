import { accessToken } from './auth/token';
import type {
  AuditEntry,
  CycleReport,
  Finding,
  MetricsReport,
  ProposalDetail,
  ProposalSummary,
  VerificationResult
} from './types';

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = await accessToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  if (init.body) {
    headers.set('Content-Type', 'application/json');
  }
  const response = await fetch(path, { ...init, headers });
  if (!response.ok) {
    let detail = response.statusText;
    try {
      const problem = (await response.json()) as { detail?: string };
      detail = problem.detail ?? detail;
    } catch {
      // not a problem+json body
    }
    throw new ApiError(response.status, detail);
  }
  return (await response.json()) as T;
}

const post = <T,>(path: string, body: unknown = {}) =>
  request<T>(path, { method: 'POST', body: JSON.stringify(body) });

export const api = {
  metrics: () => request<MetricsReport>('/api/v1/metrics'),
  findings: () => request<Finding[]>('/api/v1/findings'),
  proposals: () => request<ProposalSummary[]>('/api/v1/proposals'),
  proposal: (id: number) => request<ProposalDetail>(`/api/v1/proposals/${id}`),
  // The approver is taken from the token on the server; the body only carries a comment.
  approve: (id: number, comment: string) => post<ProposalSummary>(`/api/v1/proposals/${id}/approve`, { comment }),
  reject: (id: number, comment: string) => post<ProposalSummary>(`/api/v1/proposals/${id}/reject`, { comment }),
  runAgent: () => post<CycleReport>('/api/v1/agent/run'),
  audit: (limit = 100) => request<AuditEntry[]>(`/api/v1/audit?limit=${limit}`),
  verifyAudit: () => request<VerificationResult>('/api/v1/audit/verify')
};
