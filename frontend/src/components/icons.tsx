/** Small inline icons (no icon library dependency). */
const base = { fill: 'none', stroke: 'currentColor', strokeWidth: 2, strokeLinecap: 'round', strokeLinejoin: 'round' } as const;

export const Icon = {
  Shield: (p: { className?: string }) => (
    <svg viewBox="0 0 24 24" className={p.className ?? 'h-4 w-4'} {...base}>
      <path d="M12 3l8 3v6c0 5-3.4 8.6-8 10-4.6-1.4-8-5-8-10V6z" /><path d="M9 12l2 2 4-4" />
    </svg>
  ),
  Grid: (p: { className?: string }) => (
    <svg viewBox="0 0 24 24" className={p.className ?? 'h-4 w-4'} {...base}>
      <rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" /><rect x="14" y="14" width="7" height="7" rx="1.5" />
    </svg>
  ),
  Search: (p: { className?: string }) => (
    <svg viewBox="0 0 24 24" className={p.className ?? 'h-4 w-4'} {...base}>
      <circle cx="11" cy="11" r="7" /><path d="M20 20l-3.5-3.5" />
    </svg>
  ),
  Git: (p: { className?: string }) => (
    <svg viewBox="0 0 24 24" className={p.className ?? 'h-4 w-4'} {...base}>
      <circle cx="6" cy="6" r="2.5" /><circle cx="6" cy="18" r="2.5" /><circle cx="18" cy="8" r="2.5" />
      <path d="M6 8.5v7" /><path d="M18 10.5c0 4-6 3-11 6" />
    </svg>
  ),
  Chain: (p: { className?: string }) => (
    <svg viewBox="0 0 24 24" className={p.className ?? 'h-4 w-4'} {...base}>
      <path d="M10 14a4 4 0 005.7 0l3-3a4 4 0 00-5.7-5.7l-1 1" /><path d="M14 10a4 4 0 00-5.7 0l-3 3a4 4 0 005.7 5.7l1-1" />
    </svg>
  ),
  Check: (p: { className?: string }) => (
    <svg viewBox="0 0 24 24" className={p.className ?? 'h-4 w-4'} {...base} strokeWidth={2.6}>
      <path d="M5 12.5l4.5 4.5L19 7.5" />
    </svg>
  ),
  Clock: (p: { className?: string }) => (
    <svg viewBox="0 0 24 24" className={p.className ?? 'h-4 w-4'} {...base}>
      <circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" />
    </svg>
  ),
  Play: (p: { className?: string }) => (
    <svg viewBox="0 0 24 24" className={p.className ?? 'h-4 w-4'} fill="currentColor">
      <path d="M8 5.5v13a1 1 0 001.5.9l10.5-6.5a1 1 0 000-1.8L9.5 4.6A1 1 0 008 5.5z" />
    </svg>
  ),
  Spark: (p: { className?: string }) => (
    <svg viewBox="0 0 24 24" className={p.className ?? 'h-4 w-4'} {...base}>
      <path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5L18 18M6 18l2.5-2.5M15.5 8.5L18 6" />
    </svg>
  ),
  Pulse: (p: { className?: string }) => (
    <svg viewBox="0 0 24 24" className={p.className ?? 'h-4 w-4'} {...base}>
      <path d="M3 12h4l2.5-6 4 12 2.5-6H21" />
    </svg>
  ),
  Bot: (p: { className?: string }) => (
    <svg viewBox="0 0 24 24" className={p.className ?? 'h-4 w-4'} {...base}>
      <rect x="4" y="8" width="16" height="11" rx="3" /><path d="M12 4v4M9 13h.01M15 13h.01M9.5 16h5" />
    </svg>
  ),
  Sun: (p: { className?: string }) => (
    <svg viewBox="0 0 24 24" className={p.className ?? 'h-4 w-4'} {...base}>
      <circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.4 1.4M17.6 17.6L19 19M5 19l1.4-1.4M17.6 6.4L19 5" />
    </svg>
  ),
  Moon: (p: { className?: string }) => (
    <svg viewBox="0 0 24 24" className={p.className ?? 'h-4 w-4'} {...base}>
      <path d="M20 14.5A8 8 0 019.5 4a8 8 0 1010.5 10.5z" />
    </svg>
  ),
  Arrow: (p: { className?: string }) => (
    <svg viewBox="0 0 24 24" className={p.className ?? 'h-4 w-4'} {...base}>
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  )
};
