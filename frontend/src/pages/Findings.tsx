import { api } from '../api';
import { useAsync } from '../useAsync';
import { Badge, Empty, ErrorBox, Panel, pct } from '../components/ui';

export function Findings() {
  const { data, error } = useAsync(api.findings);
  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold">Findings</h1>
      {error && <ErrorBox message={error} />}
      <Panel title="Ranked by priority score">
        {!data?.length ? (
          <Empty>No findings yet. Report CI runs or enable SonarQube.</Empty>
        ) : (
          <table className="w-full text-left text-sm">
            <thead className="text-xs text-ink-500">
              <tr>
                <th className="pb-2 font-normal">Priority</th>
                <th className="pb-2 font-normal">Finding</th>
                <th className="pb-2 font-normal">Severity</th>
                <th className="pb-2 font-normal">Flakiness</th>
                <th className="pb-2 font-normal">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-700">
              {data.map((f) => (
                <tr key={f.id} className="align-top">
                  <td className="tabular py-2 font-mono">{Number(f.priorityScore).toFixed(2)}</td>
                  <td className="py-2">
                    <div>{f.title}</div>
                    <div className="mt-0.5 font-mono text-xs text-ink-500">{f.project} · {f.source}</div>
                  </td>
                  <td className="py-2"><Badge value={f.severity} /></td>
                  <td className="tabular py-2 font-mono">{pct(Number(f.flakinessRate))}</td>
                  <td className="py-2"><Badge value={f.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  );
}
