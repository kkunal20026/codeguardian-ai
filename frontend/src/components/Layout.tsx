import { useState, type ReactNode } from 'react';
import { useAuth } from '../auth/AuthProvider';
import { authMode } from '../auth/config';
import { navigate } from '../router';
import { Icon } from './icons';

const nav = [
  { path: '/', label: 'Overview', icon: Icon.Grid },
  { path: '/findings', label: 'Findings', icon: Icon.Search },
  { path: '/proposals', label: 'Proposals', icon: Icon.Git },
  { path: '/audit', label: 'Audit trail', icon: Icon.Chain }
];

type Theme = 'light' | 'dark';

function useTheme(): [Theme, () => void] {
  const [theme, setTheme] = useState<Theme>(
    (document.documentElement.dataset.theme as Theme | undefined) ?? 'light');
  const toggle = () => {
    const next: Theme = theme === 'light' ? 'dark' : 'light';
    document.documentElement.dataset.theme = next;
    localStorage.setItem('cg-theme', next);
    setTheme(next);
  };
  return [theme, toggle];
}

export function Logo() {
  return (
    <div className="flex items-center gap-2.5">
      <div className="grid h-9 w-9 place-items-center rounded-xl bg-gradient-to-br from-emerald-500 to-emerald-700 text-white shadow-[0_6px_16px_-6px_rgb(16_185_129/0.7)]">
        <Icon.Shield className="h-5 w-5" />
      </div>
      <div className="leading-tight">
        <div className="text-[17px] font-extrabold tracking-tight text-ink-100">
          CodeGuardian <span className="text-signal">AI</span>
        </div>
        <div className="text-[11px] font-medium text-ink-500">Agentic code health engineer</div>
      </div>
    </div>
  );
}

export function Layout({ path, children }: { path: string; children: ReactNode }) {
  const { userName, roles, logout } = useAuth();
  const [theme, toggleTheme] = useTheme();
  const active = (p: string) => (p === '/' ? path === '/' : path.startsWith(p));

  return (
    <div className="flex h-full">
      <aside className="flex w-64 shrink-0 flex-col border-r border-ink-700 bg-ink-900">
        <div className="px-5 py-5">
          <Logo />
        </div>
        <nav className="mt-2 flex flex-col gap-1 px-3">
          {nav.map((item) => (
            <a
              key={item.path}
              href={item.path}
              onClick={(e) => {
                e.preventDefault();
                navigate(item.path);
              }}
              className={`flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition ${
                active(item.path)
                  ? 'bg-signal/10 text-signal'
                  : 'text-ink-300 hover:bg-ink-800 hover:text-ink-100'
              }`}
            >
              <item.icon className="h-[18px] w-[18px]" />
              {item.label}
            </a>
          ))}
        </nav>

        <div className="mx-3 mt-6 rounded-xl border border-ink-700 bg-ink-800/60 p-3 text-xs text-ink-300">
          <div className="flex items-center gap-2 font-semibold text-ink-100">
            <span className="live-dot h-2 w-2 rounded-full bg-signal" /> Agent online
          </div>
          <p className="mt-1.5 leading-relaxed">
            Monitors CI, writes fixes with GenAI, and lets deterministic rules decide what ships.
          </p>
        </div>

        <div className="mt-auto border-t border-ink-700 px-5 py-4 text-xs">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <div className="grid h-8 w-8 place-items-center rounded-full bg-signal/15 font-bold uppercase text-signal">
                {userName.slice(0, 1)}
              </div>
              <div>
                <div className="font-semibold text-ink-100">{userName}</div>
                <div className="text-ink-500">{roles.join(' · ').toLowerCase() || authMode}</div>
              </div>
            </div>
            <button
              type="button"
              onClick={toggleTheme}
              title={theme === 'light' ? 'Dark mode' : 'Light mode'}
              className="grid h-8 w-8 place-items-center rounded-lg border border-ink-700 text-ink-300 hover:bg-ink-800 hover:text-ink-100"
            >
              {theme === 'light' ? <Icon.Moon /> : <Icon.Sun />}
            </button>
          </div>
          {authMode === 'keycloak' && (
            <button className="mt-3 text-ink-300 underline hover:text-ink-100" onClick={logout}>
              Sign out
            </button>
          )}
        </div>
      </aside>
      <main className="hero-glow min-w-0 flex-1 overflow-y-auto">
        <div className="mx-auto max-w-6xl px-8 py-8">{children}</div>
      </main>
    </div>
  );
}
