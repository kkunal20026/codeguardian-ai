import type { AuditEntry, ProposalSummary, VerificationResult } from '../types';
import { navigate } from '../router';
import { Badge, time } from './ui';
import { Icon } from './icons';

type State = 'done' | 'pending' | 'failed';
interface Step { seq: number; title: string; detail: string; at: string; state: State }

const SHOWN = new Set([
  'AGENT_CYCLE_STARTED', 'FINDING_PRIORITISED', 'LLM_CALL_COMPLETED', 'LLM_CALL_FAILED', 'PROPOSAL_GENERATED',
  'VALIDATION_COMPLETED', 'RISK_ASSESSED', 'APPROVAL_REQUESTED', 'APPROVAL_DECIDED', 'EXECUTION_SUCCEEDED',
  'EXECUTION_DRY_RUN', 'EXECUTION_FAILED', 'AGENT_CYCLE_COMPLETED'
]);

function parse(json: string): Record<string, unknown> {
  try {
    return JSON.parse(json) as Record<string, unknown>;
  } catch {
    return {};
  }
}

const num = (v: unknown, digits = 2) => (typeof v === 'number' ? v.toFixed(digits) : String(v ?? ''));

/** Turns raw, hash-chained audit events into a readable story. Nothing here is invented: every line is an event. */
function describe(e: AuditEntry): Omit<Step, 'seq' | 'at' | 'state'> & { state?: State } {
  const p = parse(e.payloadJson);
  switch (e.eventType) {
    case 'AGENT_CYCLE_STARTED':
      return { title: 'Agent cycle started', detail: 'Reading CI history and SonarQube for health problems' };
    case 'FINDING_PRIORITISED':
      return { title: 'Finding prioritised', detail: `Priority ${num(p.priority)} from severity, flakiness, impact and recency` };
    case 'LLM_CALL_COMPLETED':
      return {
        title: p.provider === 'template' ? 'Fix written by rule engine' : `Fix written by ${String(p.model ?? 'LLM')}`,
        detail: `Prompt and output stored, SHA-256 in the audit chain · ${String(p.latencyMs ?? '?')} ms`
      };
    case 'LLM_CALL_FAILED':
      return { title: 'Model call failed', detail: 'Fell back to the deterministic generator', state: 'failed' };
    case 'PROPOSAL_GENERATED':
      return { title: 'Fix proposal drafted', detail: `Confidence ${num(p.confidence)} · runbooks retrieved with ${String(p.retrieval ?? 'search')}` };
    case 'VALIDATION_COMPLETED':
      return {
        title: p.status === 'PASSED' ? `Tests green ${String(p.greenRuns)} times in a row` : 'Patch validated',
        detail: p.applies ? `Applies cleanly to the source · ${String(p.status).replaceAll('_', ' ').toLowerCase()}`
          : 'Patch does not apply',
        state: p.status === 'FAILED' ? 'failed' : undefined
      };
    case 'RISK_ASSESSED': {
      const reasons = Array.isArray(p.reasons) ? (p.reasons as string[]) : [];
      return { title: `Risk policy decided: ${String(p.riskLevel)}`, detail: reasons[0] ?? 'Deterministic rules, not the model' };
    }
    case 'APPROVAL_REQUESTED':
      return { title: 'Waiting for a human', detail: `Review requested via ${(p.channels as string[] | undefined)?.join(', ') ?? 'UI'}` };
    case 'APPROVAL_DECIDED':
      return {
        title: `${p.decision === 'APPROVED' ? 'Approved' : 'Rejected'} by ${e.actor}`,
        detail: typeof p.comment === 'string' && p.comment ? `“${p.comment}”` : `Via ${String(p.channel ?? 'UI').toLowerCase()}`,
        state: p.decision === 'APPROVED' ? 'done' : 'failed'
      };
    case 'EXECUTION_SUCCEEDED':
      return { title: p.target === 'JIRA_TICKET' ? 'Jira ticket created' : 'Pull request opened', detail: String(p.externalRef ?? 'Done') };
    case 'EXECUTION_DRY_RUN':
      return { title: `${p.target === 'JIRA_TICKET' ? 'Ticket' : 'Pull request'} step recorded`, detail: 'Dry run: integration not configured, no fake links' };
    case 'EXECUTION_FAILED':
      return { title: 'Execution failed', detail: String(p.error ?? ''), state: 'failed' };
    case 'AGENT_CYCLE_COMPLETED':
      return { title: 'Cycle complete', detail: `${String(p.planned)} planned · ${String(p.proposals)} proposals · ${String(p.durationMs)} ms` };
    default:
      return { title: e.eventType, detail: '' };
  }
}

export function buildSteps(entries: AuditEntry[], max = 7): Step[] {
  // entries arrive newest first; walk back to the latest cycle start and tell the story forward from there.
  const start = entries.findIndex((e) => e.eventType === 'AGENT_CYCLE_STARTED');
  const window = (start >= 0 ? entries.slice(0, start + 1) : entries).filter((e) => SHOWN.has(e.eventType)).reverse();
  const steps = window.map((e) => {
    const d = describe(e);
    return { seq: e.seq, at: e.eventTime, title: d.title, detail: d.detail, state: d.state ?? 'done' } as Step;
  });
  const last = steps.at(-1);
  if (last && window.at(-1)?.eventType === 'APPROVAL_REQUESTED') {
    last.state = 'pending';
  }
  return steps.slice(-max);
}

function Dot({ state }: { state: State }) {
  if (state === 'pending') {
    return (
      <div className="live-dot grid h-8 w-8 place-items-center rounded-full border-2 border-warn bg-ink-900 text-warn">
        <Icon.Clock className="h-4 w-4" />
      </div>
    );
  }
  if (state === 'failed') {
    return <div className="grid h-8 w-8 place-items-center rounded-full bg-alarm text-white">!</div>;
  }
  return (
    <div className="grid h-8 w-8 place-items-center rounded-full bg-signal text-white">
      <Icon.Check className="h-4 w-4" />
    </div>
  );
}

export function AgentTimeline({ entries, proposal, verification }: {
  entries: AuditEntry[] | null; proposal: ProposalSummary | null; verification: VerificationResult | null;
}) {
  const steps = entries ? buildSteps(entries) : [];
  return (
    <div className="relative pb-8">
      <div className="card rise p-5 pb-12">
        <div className="flex items-center justify-between">
          <div className="text-base font-bold text-ink-100">Agent cycle</div>
          <span className="inline-flex items-center gap-1.5 rounded-full border border-signal/30 bg-signal/10 px-3 py-1 text-xs font-bold text-signal">
            <span className="live-dot h-1.5 w-1.5 rounded-full bg-signal" /> LIVE
          </span>
        </div>

        {proposal && (
          <button
            type="button"
            onClick={() => navigate(`/proposals/${proposal.id}`)}
            className="mt-4 flex w-full items-center gap-3 rounded-xl border border-ink-700 bg-ink-800/70 p-3 text-left transition hover:border-signal/40"
          >
            <div className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-ink-100 text-ink-900">
              <Icon.Bot className="h-5 w-5" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="truncate text-sm font-semibold text-ink-100">{proposal.title}</div>
              <div className="truncate text-xs text-ink-500">{proposal.generatedBy} · confidence {Number(proposal.confidence).toFixed(2)}</div>
            </div>
            <Badge value={proposal.riskLevel} />
          </button>
        )}

        {steps.length === 0 ? (
          <div className="py-10 text-center text-sm text-ink-500">Press Run agent cycle to watch the agent work.</div>
        ) : (
          <ol className="mt-5">
            {steps.map((s, i) => (
              <li key={s.seq} className="rise relative flex gap-3.5 pb-5 last:pb-0" style={{ animationDelay: `${i * 70}ms` }}>
                {i < steps.length - 1 && <span className="absolute left-4 top-9 h-[calc(100%-2.25rem)] w-px bg-ink-700" />}
                <Dot state={s.state} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-baseline justify-between gap-3">
                    <div className={`text-sm font-semibold ${s.state === 'pending' ? 'text-warn' : 'text-ink-100'}`}>{s.title}</div>
                    <div className="tabular shrink-0 text-xs text-ink-500">{s.state === 'pending' ? 'Now' : time(s.at)}</div>
                  </div>
                  <div className="mt-0.5 truncate text-xs text-ink-500" title={s.detail}>{s.detail}</div>
                </div>
              </li>
            ))}
          </ol>
        )}
      </div>

      {verification && (
        <div className="card rise absolute -left-6 bottom-0 flex items-center gap-3 px-4 py-3" style={{ animationDelay: '600ms' }}>
          <div className={`grid h-9 w-9 place-items-center rounded-lg ${verification.valid ? 'bg-signal/10 text-signal' : 'bg-alarm/10 text-alarm'}`}>
            <Icon.Chain className="h-5 w-5" />
          </div>
          <div>
            <div className="text-sm font-semibold text-ink-100">
              {verification.valid ? 'Audit chain intact' : 'Audit chain broken'}
            </div>
            <div className="text-xs text-ink-500">{verification.entriesChecked} events · SHA-256 verified</div>
          </div>
        </div>
      )}
    </div>
  );
}
