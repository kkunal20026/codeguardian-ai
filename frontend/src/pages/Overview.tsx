import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth/AuthProvider';
import { navigate } from '../router';
import type { CycleReport } from '../types';
import { useAsync } from '../useAsync';
import { Badge, Button, Empty, ErrorBox, Panel, Stat, pct } from '../components/ui';

export function Overview() {
  const metrics = useAsync(api.metrics);
  const proposals = useAsync(api.proposals);
  const { hasRole } = useAuth();
  const [cycle, setCycle] = useState<CycleReport | null>(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function runAgent() {
    setRunning(true);
    setError(null);
    try {
      setCycle(await api.runAgent());
      metrics.reload();
      proposals.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setRunning(false);
    }
  }

  const m = metrics.data;
  const noise = m?.ciNoise;

  return (
    <div className="space-y-6">
      <div className="flex items-end justify-between">
        <div>
          <h1 className="text-xl font-semibold">Overview</h1>
          <p className="mt-1 text-sm text-ink-300">
            Monitor → plan → generate → validate → risk policy → approve → execute → audit
          </p>
        </div>
        <Button onClick={runAgent} disabled={running || !hasRole('APPROVER')}
                title={hasRole('APPROVER') ? 'Run one agent cycle' : 'Requires APPROVER role'}>
          {running ? 'Agent running…' : 'Run agent cycle'}
        </Button>
      </div>

      {error && <ErrorBox message={error} />}
      {metrics.error && <ErrorBox message={`Metrics unavailable: ${metrics.error}`} />}

      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <Stat label="CI noise reduction"
              value={noise?.reductionPercent == null ? '—' : `${noise.reductionPercent.toFixed(0)}%`}
              hint={noise?.reductionPercent == null
                ? 'Needs enough runs before and after a fix'
                : `${pct(noise.before)} → ${pct(noise.after)} over ${noise.runsBefore}/${noise.runsAfter} runs`} />
        <Stat label="Flaky failure rate" value={pct(m?.flakyTestRate, 1)} hint="Flaky failures / test executions" />
        <Stat label="Automated fixes" value={m?.automatedFixes ?? '—'} hint="LOW risk, approved by policy" />
        <Stat label="False positive rate" value={pct(m?.falsePositiveRate)} hint="Rejected / decided proposals" />
      </div>

      {m && (
        <p className="text-xs text-ink-500">
          {m.totalRuns} CI runs analysed. {m.note} LLM fallbacks: {m.llmFallbacks}.
        </p>
      )}

      {cycle && (
        <Panel title={`Last cycle · ${cycle.durationMs} ms`}>
          <div className="text-sm text-ink-300">
            {cycle.findingsRefreshed} findings refreshed, {cycle.findingsPlanned} planned.
          </div>
          <ul className="mt-3 space-y-2">
            {cycle.outcomes.map((o) => (
              <li key={o.proposalId} className="flex items-center gap-3 text-sm">
                <Badge value={o.riskLevel} />
                <Badge value={o.status} />
                <a className="text-ink-100 hover:underline" href={`/proposals/${o.proposalId}`}
                   onClick={(e) => { e.preventDefault(); navigate(`/proposals/${o.proposalId}`); }}>
                  {o.title}
                </a>
                <span className="ml-auto font-mono text-xs text-ink-500">{o.generatedBy}</span>
              </li>
            ))}
          </ul>
          {cycle.errors.map((e) => <ErrorBox key={e} message={e} />)}
        </Panel>
      )}

      <Panel title="Waiting for a human">
        {proposals.data?.filter((p) => p.status === 'AWAITING_APPROVAL').length ? (
          <ul className="divide-y divide-ink-700">
            {proposals.data.filter((p) => p.status === 'AWAITING_APPROVAL').map((p) => (
              <li key={p.id} className="flex items-center gap-3 py-2 text-sm">
                <Badge value={p.riskLevel} />
                <a className="hover:underline" href={`/proposals/${p.id}`}
                   onClick={(e) => { e.preventDefault(); navigate(`/proposals/${p.id}`); }}>
                  {p.title}
                </a>
                <span className="tabular ml-auto font-mono text-xs text-ink-500">
                  confidence {Number(p.confidence).toFixed(2)}
                </span>
              </li>
            ))}
          </ul>
        ) : (
          <Empty>Nothing pending. Run the agent cycle to triage open findings.</Empty>
        )}
      </Panel>
    </div>
  );
}
