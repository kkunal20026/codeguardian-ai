import type { ReactNode } from 'react';

const tone: Record<string, string> = {
  LOW: 'text-signal border-signal/40 bg-signal/10',
  MEDIUM: 'text-warn border-warn/40 bg-warn/10',
  HIGH: 'text-alarm border-alarm/40 bg-alarm/10',
  EXECUTED: 'text-signal border-signal/40 bg-signal/10',
  SUCCEEDED: 'text-signal border-signal/40 bg-signal/10',
  PASSED: 'text-signal border-signal/40 bg-signal/10',
  AWAITING_APPROVAL: 'text-warn border-warn/40 bg-warn/10',
  TICKET_CREATED: 'text-sky-300 border-sky-400/40 bg-sky-400/10',
  DRY_RUN: 'text-ink-300 border-ink-500 bg-ink-800',
  NOT_EXECUTED: 'text-ink-300 border-ink-500 bg-ink-800',
  REJECTED: 'text-alarm border-alarm/40 bg-alarm/10',
  FAILED: 'text-alarm border-alarm/40 bg-alarm/10'
};

export function Badge({ value }: { value: string }) {
  return (
    <span
      className={`inline-flex items-center rounded border px-1.5 py-0.5 font-mono text-[11px] uppercase tracking-wide ${
        tone[value] ?? 'text-ink-300 border-ink-700 bg-ink-800'
      }`}
    >
      {value.replaceAll('_', ' ')}
    </span>
  );
}

export function Panel({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="rounded-lg border border-ink-700 bg-ink-900">
      <header className="flex items-center justify-between border-b border-ink-700 px-4 py-2.5">
        <h2 className="text-xs font-medium uppercase tracking-wider text-ink-300">{title}</h2>
        {action}
      </header>
      <div className="p-4">{children}</div>
    </section>
  );
}

export function Stat({ label, value, hint }: { label: string; value: ReactNode; hint?: string }) {
  return (
    <div className="rounded-lg border border-ink-700 bg-ink-900 px-4 py-3">
      <div className="text-xs text-ink-300">{label}</div>
      <div className="tabular mt-1 font-mono text-2xl text-ink-100">{value}</div>
      {hint && <div className="mt-1 text-[11px] text-ink-500">{hint}</div>}
    </div>
  );
}

export function ErrorBox({ message }: { message: string }) {
  return <div className="rounded border border-alarm/40 bg-alarm/10 px-3 py-2 text-sm text-alarm">{message}</div>;
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="py-8 text-center text-sm text-ink-500">{children}</div>;
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
    primary: 'bg-signal text-ink-950 hover:bg-emerald-300',
    danger: 'border border-alarm/50 text-alarm hover:bg-alarm/10',
    ghost: 'border border-ink-700 text-ink-100 hover:bg-ink-800'
  }[variant];
  return (
    <button
      type="button"
      title={title}
      onClick={onClick}
      disabled={disabled}
      className={`rounded px-3 py-1.5 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-40 ${styles}`}
    >
      {children}
    </button>
  );
}

export const pct = (value: number | null | undefined, digits = 0) =>
  value === null || value === undefined ? '—' : `${(value * 100).toFixed(digits)}%`;

export const when = (iso: string | null) => (iso ? new Date(iso).toLocaleString() : '—');
