import type { ReactNode } from 'react';

const tone: Record<string, string> = {
  LOW: 'text-signal border-signal/30 bg-signal/10',
  MEDIUM: 'text-warn border-warn/30 bg-warn/10',
  HIGH: 'text-alarm border-alarm/30 bg-alarm/10',
  EXECUTED: 'text-signal border-signal/30 bg-signal/10',
  SUCCEEDED: 'text-signal border-signal/30 bg-signal/10',
  PASSED: 'text-signal border-signal/30 bg-signal/10',
  APPROVED: 'text-signal border-signal/30 bg-signal/10',
  AWAITING_APPROVAL: 'text-warn border-warn/30 bg-warn/10',
  PROPOSED: 'text-info border-info/30 bg-info/10',
  OPEN: 'text-info border-info/30 bg-info/10',
  TICKET_CREATED: 'text-info border-info/30 bg-info/10',
  DRY_RUN: 'text-ink-300 border-ink-700 bg-ink-800',
  NOT_EXECUTED: 'text-ink-300 border-ink-700 bg-ink-800',
  REJECTED: 'text-alarm border-alarm/30 bg-alarm/10',
  FAILED: 'text-alarm border-alarm/30 bg-alarm/10',
  BLOCKER: 'text-alarm border-alarm/30 bg-alarm/10',
  CRITICAL: 'text-alarm border-alarm/30 bg-alarm/10',
  MAJOR: 'text-warn border-warn/30 bg-warn/10'
};

export function Badge({ value }: { value: string }) {
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wide ${
        tone[value] ?? 'text-ink-300 border-ink-700 bg-ink-800'
      }`}
    >
      {value.replaceAll('_', ' ')}
    </span>
  );
}

export function Panel({ title, action, children, className = '' }: {
  title: string; action?: ReactNode; children: ReactNode; className?: string;
}) {
  return (
    <section className={`card overflow-hidden ${className}`}>
      <header className="flex items-center justify-between border-b border-ink-700 px-5 py-3.5">
        <h2 className="text-sm font-semibold text-ink-100">{title}</h2>
        {action}
      </header>
      <div className="p-5">{children}</div>
    </section>
  );
}

/** A KPI card: big number, a short label, and an optional honest 0..1 gauge instead of invented trend lines. */
export function Stat({ label, value, hint, gauge, accent = 'signal', icon }: {
  label: string; value: ReactNode; hint?: string; gauge?: number | null;
  accent?: 'signal' | 'warn' | 'info' | 'alarm'; icon?: ReactNode;
}) {
  const text = { signal: 'text-signal', warn: 'text-warn', info: 'text-info', alarm: 'text-alarm' }[accent];
  const bar = { signal: 'bg-signal', warn: 'bg-warn', info: 'bg-info', alarm: 'bg-alarm' }[accent];
  const soft = { signal: 'bg-signal/10', warn: 'bg-warn/10', info: 'bg-info/10', alarm: 'bg-alarm/10' }[accent];
  return (
    <div className="card rise px-5 py-4">
      <div className="flex items-center justify-between">
        <div className="text-sm font-medium text-ink-300">{label}</div>
        {icon && <div className={`grid h-8 w-8 place-items-center rounded-lg ${soft} ${text}`}>{icon}</div>}
      </div>
      <div className={`tabular mt-2 text-4xl font-extrabold tracking-tight ${text}`}>{value}</div>
      {gauge != null && (
        <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-ink-800">
          <div className={`h-full rounded-full ${bar} transition-all duration-700`}
               style={{ width: `${Math.max(2, Math.min(100, (gauge ?? 0) * 100))}%` }} />
        </div>
      )}
      {hint && <div className="mt-2 text-xs text-ink-500">{hint}</div>}
    </div>
  );
}

export function PageHeader({ eyebrow, title, subtitle, action }: {
  eyebrow?: string; title: string; subtitle?: ReactNode; action?: ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div>
        {eyebrow && (
          <div className="flex items-center gap-3 text-xs font-bold uppercase tracking-[0.18em] text-signal">
            <span className="h-0.5 w-6 rounded bg-signal" />
            {eyebrow}
          </div>
        )}
        <h1 className="mt-2 text-3xl font-extrabold tracking-tight text-ink-100">{title}</h1>
        {subtitle && <p className="mt-1.5 max-w-2xl text-[15px] leading-relaxed text-ink-300">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}

export function ErrorBox({ message }: { message: string }) {
  return <div className="rounded-xl border border-alarm/30 bg-alarm/10 px-4 py-3 text-sm text-alarm">{message}</div>;
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="py-10 text-center text-sm text-ink-500">{children}</div>;
}

export function Button({
  children,
  onClick,
  disabled,
  variant = 'primary',
  title
}: {
  children: ReactNode;
  onClick: () => void;
  disabled?: boolean;
  variant?: 'primary' | 'danger' | 'ghost';
  title?: string;
}) {
  const styles = {
    primary: 'bg-signal text-[var(--on-signal)] shadow-[0_8px_20px_-8px_var(--signal)] hover:brightness-110',
    danger: 'border border-alarm/40 text-alarm hover:bg-alarm/10',
    ghost: 'border border-ink-700 bg-ink-900 text-ink-100 hover:bg-ink-800'
  }[variant];
  return (
    <button
      type="button"
      title={title}
      onClick={onClick}
      disabled={disabled}
      className={`inline-flex items-center gap-2 rounded-full px-5 py-2.5 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-40 ${styles}`}
    >
      {children}
    </button>
  );
}

/** Table header cell and row styles shared by the list pages. */
export const th = 'pb-3 text-xs font-semibold uppercase tracking-wider text-ink-500';
export const td = 'py-3';

export const pct = (value: number | null | undefined, digits = 0) =>
  value === null || value === undefined ? '—' : `${(value * 100).toFixed(digits)}%`;

export const when = (iso: string | null) => (iso ? new Date(iso).toLocaleString() : '—');

export const time = (iso: string | null) =>
  iso ? new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—';
