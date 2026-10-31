import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth/AuthProvider';
import { navigate } from '../router';
import type { CycleReport } from '../types';
import { useAsync } from '../useAsync';
import { AgentTimeline } from '../components/AgentTimeline';
import { Icon } from '../components/icons';
import { Badge, Button, Empty, ErrorBox, Panel, Stat, pct } from '../components/ui';

const stages = ['Monitor', 'Plan', 'Retrieve', 'Generate', 'Validate', 'Risk policy', 'Approve', 'Execute', 'Audit'];

export function Overview() {
  const metrics = useAsync(api.metrics);
  const proposals = useAsync(api.proposals);
  const audit = useAsync(() => api.audit(80));
  const verification = useAsync(api.verifyAudit);
  const { hasRole } = useAuth();
  const [cycle, setCycle] = useState<CycleReport | null>(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function runAgent() {
    setRunning(true);
    setError(null);
    try {
      setCycle(await api.runAgent());
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setRunning(false);
      metrics.reload();
      proposals.reload();
      audit.reload();
      verification.reload();
    }
  }

  const m = metrics.data;
  const noise = m?.ciNoise;
  const pending = proposals.data?.filter((p) => p.status === 'AWAITING_APPROVAL') ?? [];
  const latest = proposals.data?.[0] ?? null;

  return (
    <div className="space-y-8">
      {/* Hero */}
      <div className="grid items-start gap-10 lg:grid-cols-[1.1fr_1fr]">
        <div className="pt-2">
          <div className="flex items-center gap-3 text-xs font-bold uppercase tracking-[0.18em] text-signal">
            <span className="h-0.5 w-6 rounded bg-signal" />
            Agentic code health · Generative AI
          </div>
          <h1 className="mt-4 text-5xl font-extrabold leading-[1.05] tracking-tight text-ink-100">
            Flaky tests fixed
            <br />
            <span className="text-signal">before they cost a sprint.</span>
          </h1>
          <p className="mt-5 max-w-xl text-[17px] leading-relaxed text-ink-300">
            CodeGuardian watches CI and SonarQube, ranks what hurts most, and writes the fix with GenAI.
            It proves the fix with tests, then <strong className="text-ink-100">deterministic risk rules decide</strong>{' '}
            whether to open a PR, ask a human, or raise a ticket. Every step is hash-chained.
          </p>
          <div className="mt-7 flex flex-wrap items-center gap-3">
            <Button onClick={runAgent} disabled={running || !hasRole('APPROVER')}
                    title={hasRole('APPROVER') ? 'Run one agent cycle' : 'Requires APPROVER role'}>
              <Icon.Play className="h-4 w-4" />
              {running ? 'Agent running…' : 'Run agent cycle'}
            </Button>
            <Button variant="ghost" onClick={() => navigate('/audit')}>
              <Icon.Chain className="h-4 w-4" /> View audit trail
            </Button>
          </div>
          <div className="mt-5 flex flex-wrap gap-x-5 gap-y-2 text-sm text-ink-300">
            {['Model proposes, rules decide', 'Human in the loop', 'Tamper-evident audit'].map((t) => (
              <span key={t} className="inline-flex items-center gap-1.5">
                <Icon.Check className="h-4 w-4 text-signal" /> {t}
              </span>
            ))}
          </div>
          {m && (
            <p className="mt-5 flex items-center gap-2 text-sm text-ink-500">
              <span className="h-2 w-2 rounded-full bg-signal/60" />
              <span><strong className="text-ink-100">{m.totalRuns} CI runs</strong> analysed. {m.note}</span>
            </p>
          )}
        </div>

        <AgentTimeline entries={audit.data} proposal={latest} verification={verification.data} />
      </div>

      {error && <ErrorBox message={error} />}
      {metrics.error && <ErrorBox message={`Metrics unavailable: ${metrics.error}`} />}

      {/* Pipeline */}
      <div className="card flex flex-wrap items-center justify-between gap-2 px-5 py-4">
        {stages.map((s, i) => (
          <div key={s} className="flex items-center gap-2">
            <span className="grid h-6 w-6 place-items-center rounded-full bg-signal/10 text-[11px] font-bold text-signal">{i + 1}</span>
            <span className="text-sm font-semibold text-ink-100">{s}</span>
            {i < stages.length - 1 && <Icon.Arrow className="ml-1 h-3.5 w-3.5 text-ink-500" />}
          </div>
        ))}
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="CI noise reduction" icon={<Icon.Pulse />} accent="signal"
              value={noise?.reductionPercent == null ? '—' : `${noise.reductionPercent.toFixed(0)}%`}
              gauge={noise?.reductionPercent == null ? null : noise.reductionPercent / 100}
              hint={noise?.reductionPercent == null
                ? 'Measured once there are 5+ runs before and after a fix'
                : `${pct(noise.before)} → ${pct(noise.after)} over ${noise.runsBefore}/${noise.runsAfter} runs`} />
        <Stat label="Flaky failure rate" icon={<Icon.Spark />} accent="warn"
              value={pct(m?.flakyTestRate, 1)} gauge={m?.flakyTestRate ?? null}
              hint="Flaky failures / test executions" />
        <Stat label="Automated fixes" icon={<Icon.Bot />} accent="info"
              value={m?.automatedFixes ?? '—'}
              hint={`LOW risk, approved by policy · ${m?.proposalsTotal ?? 0} proposals total`} />
        <Stat label="False positive rate" icon={<Icon.Shield />} accent="alarm"
              value={pct(m?.falsePositiveRate)} gauge={m?.falsePositiveRate ?? null}
              hint="Rejected / decided proposals" />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Panel title="Waiting for a human" action={pending.length > 0 && <Badge value={`${pending.length} pending`} />}>
          {pending.length ? (
            <ul className="space-y-2">
              {pending.map((p) => (
                <li key={p.id}>
                  <a href={`/proposals/${p.id}`}
                     onClick={(e) => { e.preventDefault(); navigate(`/proposals/${p.id}`); }}
                     className="flex items-center gap-3 rounded-xl border border-ink-700 px-4 py-3 transition hover:border-signal/40 hover:bg-ink-800/60">
                    <Badge value={p.riskLevel} />
                    <span className="min-w-0 flex-1 truncate text-sm font-medium text-ink-100">{p.title}</span>
                    <span className="tabular text-xs text-ink-500">conf. {Number(p.confidence).toFixed(2)}</span>
                    <Icon.Arrow className="h-4 w-4 text-ink-500" />
                  </a>
                </li>
              ))}
            </ul>
          ) : (
            <Empty>Nothing pending. Run the agent cycle to triage open findings.</Empty>
          )}
        </Panel>

        <Panel title={cycle ? `Last cycle · ${cycle.durationMs} ms` : 'Last cycle'}>
          {cycle ? (
            <>
              <div className="text-sm text-ink-300">
                {cycle.findingsRefreshed} findings refreshed, {cycle.findingsPlanned} planned.
              </div>
              <ul className="mt-3 space-y-2">
                {cycle.outcomes.map((o) => (
                  <li key={o.proposalId} className="flex items-center gap-2 text-sm">
                    <Badge value={o.riskLevel} />
                    <Badge value={o.status} />
                    <a className="min-w-0 flex-1 truncate font-medium text-ink-100 hover:underline" href={`/proposals/${o.proposalId}`}
                       onClick={(e) => { e.preventDefault(); navigate(`/proposals/${o.proposalId}`); }}>
                      {o.title}
                    </a>
                  </li>
                ))}
              </ul>
              <div className="mt-3 space-y-2">{cycle.errors.map((e) => <ErrorBox key={e} message={e} />)}</div>
            </>
          ) : (
            <Empty>Results of the next cycle appear here. LLM fallbacks so far: {m?.llmFallbacks ?? 0}.</Empty>
          )}
        </Panel>
      </div>
    </div>
  );
}
