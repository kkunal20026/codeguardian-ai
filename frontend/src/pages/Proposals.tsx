import { api } from '../api';
import { navigate } from '../router';
import { useAsync } from '../useAsync';
import { Badge, Empty, ErrorBox, PageHeader, Panel, th, when } from '../components/ui';

export function Proposals() {
  const { data, error } = useAsync(api.proposals);
  return (
    <div className="space-y-6">
      <PageHeader eyebrow="Generate · Validate · Decide" title="Proposals"
                  subtitle="Fixes written by the GenAI layer, validated against the real source, and routed by deterministic risk rules." />
      {error && <ErrorBox message={error} />}
      <Panel title="Generated fixes">
        {!data?.length ? (
          <Empty>No proposals yet.</Empty>
        ) : (
          <table className="w-full text-left text-sm">
            <thead>
              <tr>
                <th className={th}>Risk</th>
                <th className={th}>Title</th>
                <th className={th}>Validation</th>
                <th className={th}>Status</th>
                <th className={th}>Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-700">
              {data.map((p) => (
                <tr key={p.id} className="cursor-pointer transition hover:bg-ink-800/60" onClick={() => navigate(`/proposals/${p.id}`)}>
                  <td className="py-3"><Badge value={p.riskLevel} /></td>
                  <td className="py-3">
                    <div className="font-semibold text-ink-100">{p.title}</div>
                    <div className="mt-0.5 font-mono text-xs text-ink-500">{p.generatedBy}</div>
                  </td>
                  <td className="py-3"><Badge value={p.validationStatus} /></td>
                  <td className="py-3"><Badge value={p.status} /></td>
                  <td className="py-3 text-xs text-ink-300">{when(p.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  );
}
