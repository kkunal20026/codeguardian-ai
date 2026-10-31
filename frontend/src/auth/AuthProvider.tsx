import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import Keycloak from 'keycloak-js';
import { authMode, keycloakConfig } from './config';
import { setTokenProvider } from './token';

interface AuthState {
  ready: boolean;
  userName: string;
  roles: string[];
  error: string | null;
  hasRole: (role: string) => boolean;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

// Demo mode mirrors the backend demo principal; the backend, not this list, enforces access.
const DEMO_ROLES = ['VIEWER', 'APPROVER', 'ADMIN'];

export function AuthProvider({ children }: { children: ReactNode }) {
  const keycloak = useRef<Keycloak | null>(null);
  const [state, setState] = useState({
    ready: authMode === 'demo',
    userName: authMode === 'demo' ? 'demo-user' : '',
    roles: authMode === 'demo' ? DEMO_ROLES : ([] as string[]),
    error: null as string | null
  });

  useEffect(() => {
    if (authMode !== 'keycloak' || keycloak.current) {
      return;
    }
    const kc = new Keycloak(keycloakConfig);
    keycloak.current = kc;
    kc.init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false })
      .then(() => {
        setTokenProvider(async () => {
          try {
            await kc.updateToken(30);
            return kc.token ?? null;
          } catch {
            await kc.login();
            return null;
          }
        });
        setState({
          ready: true,
          userName: (kc.tokenParsed?.preferred_username as string | undefined) ?? 'user',
          roles: kc.realmAccess?.roles ?? [],
          error: null
        });
      })
      .catch((err: unknown) => setState((s) => ({ ...s, ready: false, error: String(err) })));
  }, []);

  const value: AuthState = {
    ...state,
    hasRole: (role) => state.roles.some((r) => r.toUpperCase() === role.toUpperCase()),
    logout: () => keycloak.current?.logout()
  };

  if (state.error) {
    return <FullScreen>Sign-in failed: {state.error}</FullScreen>;
  }
  if (!state.ready) {
    return <FullScreen>Signing in…</FullScreen>;
  }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

function FullScreen({ children }: { children: ReactNode }) {
  return <div className="flex h-full items-center justify-center text-sm text-ink-300">{children}</div>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return ctx;
}
