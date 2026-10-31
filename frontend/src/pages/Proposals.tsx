import { api } from '../api';
import { navigate } from '../router';
import { useAsync } from '../useAsync';
import { Badge, Empty, ErrorBox, Panel, when } from '../components/ui';

export function Proposals() {
  const { data, error } = useAsync(api.proposals);
  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold">Proposals</h1>
      {error && <ErrorBox message={error} />}
      <Panel title="Generated fixes">
        {!data?.length ? (
          <Empty>No proposals yet.</Empty>
        ) : (
          <table className="w-full text-left text-sm">
            <thead className="text-xs text-ink-500">
              <tr>
                <th className="pb-2 font-normal">Risk</th>
                <th className="pb-2 font-normal">Title</th>
                <th className="pb-2 font-normal">Validation</th>
                <th className="pb-2 font-normal">Status</th>
                <th className="pb-2 font-normal">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-700">
              {data.map((p) => (
                <tr key={p.id} className="cursor-pointer hover:bg-ink-800/50" onClick={() => navigate(`/proposals/${p.id}`)}>
                  <td className="py-2"><Badge value={p.riskLevel} /></td>
                  <td className="py-2">
                    <div>{p.title}</div>
                    <div className="mt-0.5 font-mono text-xs text-ink-500">{p.generatedBy}</div>
                  </td>
                  <td className="py-2"><Badge value={p.validationStatus} /></td>
                  <td className="py-2"><Badge value={p.status} /></td>
                  <td className="py-2 text-xs text-ink-300">{when(p.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  );
}
