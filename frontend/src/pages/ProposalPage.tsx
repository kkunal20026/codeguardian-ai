import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth/AuthProvider';
import { useAsync } from '../useAsync';
import { DiffView } from '../components/DiffView';
import { Badge, Button, ErrorBox, Panel, when } from '../components/ui';

export function ProposalPage({ id }: { id: number }) {
  const { data: p, error, reload } = useAsync(() => api.proposal(id), [id]);
  const { hasRole, userName } = useAuth();
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [showPrompt, setShowPrompt] = useState(false);

  async function decide(kind: 'approve' | 'reject') {
    setBusy(true);
    setActionError(null);
    try {
      await (kind === 'approve' ? api.approve(id, comment) : api.reject(id, comment));
      reload();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  }

  if (error) return <ErrorBox message={error} />;
  if (!p) return <div className="text-sm text-ink-500">Loading…</div>;

  // HIGH risk is ticket-only by policy; the backend refuses to approve it, so the UI does not offer it.
  const ticketOnly = p.riskLevel === 'HIGH';
  const canDecide = hasRole('APPROVER') && !ticketOnly
    && (p.status === 'AWAITING_APPROVAL' || p.status === 'FAILED');
  const llm = p.llmCalls.at(-1);

  return (
    <div className="space-y-6">
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <Badge value={p.riskLevel} />
          <Badge value={p.status} />
          <Badge value={p.validationStatus} />
          <span className="font-mono text-xs text-ink-500">#{p.id} · {p.generatedBy} · confidence {Number(p.confidence).toFixed(2)}</span>
        </div>
        <h1 className="mt-3 text-3xl font-extrabold tracking-tight text-ink-100">{p.title}</h1>
        <p className="mt-2 max-w-3xl text-[15px] leading-relaxed text-ink-300">{p.summary}</p>
      </div>

      <div className="grid gap-5 lg:grid-cols-5">
        <div className="space-y-5 lg:col-span-3">
          <Panel title="Patch">
            <DiffView diff={p.patchDiff ?? ''} />
          </Panel>
          <Panel title="Pull request description (generated)">
            <pre className="whitespace-pre-wrap font-sans text-sm leading-6 text-ink-100">{p.prDescription}</pre>
          </Panel>
          {p.validationLog && (
            <Panel title="Validation log">
              <pre className="max-h-64 overflow-auto whitespace-pre-wrap font-mono text-xs text-ink-300">{p.validationLog}</pre>
            </Panel>
          )}
        </div>

        <div className="space-y-5 lg:col-span-2">
          <Panel title="Why">
            <dl className="space-y-3 text-sm">
              <div>
                <dt className="text-xs text-ink-500">Root cause</dt>
                <dd className="mt-0.5">{p.rootCause}</dd>
              </div>
              <div>
                <dt className="text-xs text-ink-500">Explanation for reviewer</dt>
                <dd className="mt-0.5">{p.explanation}</dd>
              </div>
              <div>
                <dt className="text-xs text-ink-500">Risk policy</dt>
                <dd className="mt-0.5 font-mono text-xs text-ink-300">{p.riskReasons}</dd>
              </div>
            </dl>
          </Panel>

          <Panel title="Decision">
            {canDecide ? (
              <div className="space-y-3">
                <textarea
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  placeholder="Comment (stored in the audit trail)"
                  className="h-24 w-full rounded-xl border border-ink-700 bg-ink-950 p-3 text-sm outline-none focus:border-signal"
                />
                <div className="flex gap-2">
                  <Button onClick={() => decide('approve')} disabled={busy}>Approve and open PR</Button>
                  <Button variant="danger" onClick={() => decide('reject')} disabled={busy}>Reject</Button>
                </div>
                <p className="text-xs text-ink-500">Recorded as <span className="font-mono">{userName}</span> from your sign-in.</p>
              </div>
            ) : (
              <p className="text-sm text-ink-300">
                {ticketOnly
                  ? 'HIGH risk: handled through a ticket only, never a pull request.'
                  : hasRole('APPROVER') ? 'No decision needed in this state.' : 'Requires the APPROVER role.'}
              </p>
            )}
            {actionError && <div className="mt-3"><ErrorBox message={actionError} /></div>}
            {p.approvals.length > 0 && (
              <ul className="mt-4 space-y-1 border-t border-ink-700 pt-3 text-xs text-ink-300">
                {p.approvals.map((a, i) => (
                  <li key={i}>
                    <span className="font-mono text-ink-100">{a.approver}</span> {a.decision.toLowerCase()} via {a.channel} · {when(a.decidedAt)}
                    {a.comment && <div className="text-ink-500">“{a.comment}”</div>}
                  </li>
                ))}
              </ul>
            )}
          </Panel>

          <Panel title="Execution">
            {p.executions.length === 0 ? (
              <p className="text-sm text-ink-500">Nothing executed yet.</p>
            ) : (
              <ul className="space-y-2 text-sm">
                {p.executions.map((e, i) => (
                  <li key={i} className="flex flex-wrap items-center gap-2">
                    <Badge value={e.status} />
                    <span className="font-mono text-xs">{e.target}</span>
                    {e.externalRef ? (
                      <a className="text-sky-300 hover:underline" href={e.externalRef} target="_blank" rel="noreferrer">
                        {e.externalRef}
                      </a>
                    ) : (
                      <span className="text-xs text-ink-500">{e.lastError}</span>
                    )}
                    <span className="ml-auto text-xs text-ink-500">{e.attempts} attempt(s)</span>
                  </li>
                ))}
              </ul>
            )}
          </Panel>

          {llm && (
            <Panel title="Model call (audited)"
                   action={<button className="text-xs text-ink-300 hover:text-ink-100" onClick={() => setShowPrompt(!showPrompt)}>
                     {showPrompt ? 'Hide prompt' : 'Show prompt'}</button>}>
              <div className="font-mono text-xs text-ink-300">
                {llm.provider} · {llm.model} · {llm.latencyMs} ms
                {llm.promptTokens != null && ` · ${llm.promptTokens}+${llm.completionTokens} tokens`}
                {!llm.success && <span className="text-alarm"> · failed: {llm.errorMessage}</span>}
              </div>
              {showPrompt && (
                <pre className="mt-3 max-h-80 overflow-auto whitespace-pre-wrap rounded bg-ink-950 p-3 font-mono text-[11px] text-ink-300">
                  {`SYSTEM\n${llm.systemPrompt}\n\nUSER\n${llm.userPrompt}`}
                </pre>
              )}
            </Panel>
          )}
        </div>
      </div>
    </div>
  );
}
