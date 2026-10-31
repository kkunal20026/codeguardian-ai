import type { ReactNode } from 'react';
import { useAuth } from '../auth/AuthProvider';
import { authMode } from '../auth/config';
import { navigate } from '../router';

const nav = [
  { path: '/', label: 'Overview' },
  { path: '/findings', label: 'Findings' },
  { path: '/proposals', label: 'Proposals' },
  { path: '/audit', label: 'Audit trail' }
];

export function Layout({ path, children }: { path: string; children: ReactNode }) {
  const { userName, roles, logout } = useAuth();
  const active = (p: string) => (p === '/' ? path === '/' : path.startsWith(p));

  return (
    <div className="flex h-full">
      <aside className="flex w-56 shrink-0 flex-col border-r border-ink-700 bg-ink-900">
        <div className="flex items-center gap-2 px-4 py-4">
          <svg viewBox="0 0 32 32" className="h-6 w-6" aria-hidden>
            <path d="M3 17h6l3-7 4 13 3-6h10" fill="none" stroke="#34d399" strokeWidth="2.5"
                  strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          <div className="text-sm font-semibold">Code Health Agent</div>
        </div>
        <nav className="flex flex-col gap-0.5 px-2">
          {nav.map((item) => (
            <a
              key={item.path}
              href={item.path}
              onClick={(e) => {
                e.preventDefault();
                navigate(item.path);
              }}
              className={`rounded px-3 py-2 text-sm ${
                active(item.path) ? 'bg-ink-800 text-ink-100' : 'text-ink-300 hover:bg-ink-800/60 hover:text-ink-100'
              }`}
            >
              {item.label}
            </a>
          ))}
        </nav>
        <div className="mt-auto border-t border-ink-700 px-4 py-3 text-xs text-ink-300">
          <div className="font-mono text-ink-100">{userName}</div>
          <div className="mt-0.5 text-ink-500">{roles.join(' · ')}</div>
          <div className="mt-2 text-ink-500">auth: {authMode}</div>
          {authMode === 'keycloak' && (
            <button className="mt-2 text-ink-300 underline hover:text-ink-100" onClick={logout}>
              Sign out
            </button>
          )}
        </div>
      </aside>
      <main className="min-w-0 flex-1 overflow-y-auto">
        <div className="mx-auto max-w-6xl px-6 py-6">{children}</div>
      </main>
    </div>
  );
}
