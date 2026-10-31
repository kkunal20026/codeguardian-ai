import { useEffect, useState } from 'react';

/**
 * Minimal history-based router. Deep links like /proposals/42 (sent in Slack and email) work directly.
 */
export function navigate(path: string): void {
  if (path !== window.location.pathname) {
    window.history.pushState({}, '', path);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }
}

export function usePath(): string {
  const [path, setPath] = useState(window.location.pathname);
  useEffect(() => {
    const onChange = () => setPath(window.location.pathname);
    window.addEventListener('popstate', onChange);
    return () => window.removeEventListener('popstate', onChange);
  }, []);
  return path;
}
