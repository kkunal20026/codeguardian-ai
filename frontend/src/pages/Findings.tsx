import { api } from '../api';
import { useAsync } from '../useAsync';
import { Badge, Empty, ErrorBox, PageHeader, Panel, pct, th } from '../components/ui';

export function Findings() {
  const { data, error } = useAsync(api.findings);
  return (
    <div className="space-y-6">
      <PageHeader eyebrow="Monitor · Plan" title="Findings"
                  subtitle="Code health problems from CI and SonarQube, ranked by an explainable priority score: severity, flakiness, business impact and recency." />
      {error && <ErrorBox message={error} />}
      <Panel title="Ranked by priority score">
        {!data?.length ? (
          <Empty>No findings yet. Report CI runs or enable SonarQube.</Empty>
        ) : (
          <table className="w-full text-left text-sm">
            <thead>
              <tr>
                <th className={th}>Priority</th>
                <th className={th}>Finding</th>
                <th className={th}>Severity</th>
                <th className={th}>Flakiness</th>
                <th className={th}>Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-700">
              {data.map((f) => (
                <tr key={f.id} className="align-top">
                  <td className="py-3"><span className="tabular inline-grid h-9 w-12 place-items-center rounded-lg bg-signal/10 font-bold text-signal">{Number(f.priorityScore).toFixed(2)}</span></td>
                  <td className="py-3">
                    <div className="font-semibold text-ink-100">{f.title}</div>
                    <div className="mt-0.5 font-mono text-xs text-ink-500">{f.project} · {f.source}</div>
                  </td>
                  <td className="py-3"><Badge value={f.severity} /></td>
                  <td className="tabular py-3 font-mono">{pct(Number(f.flakinessRate))}</td>
                  <td className="py-3"><Badge value={f.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  );
}
