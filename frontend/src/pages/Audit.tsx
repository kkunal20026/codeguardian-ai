import { useState } from 'react';
import { api } from '../api';
import type { VerificationResult } from '../types';
import { useAsync } from '../useAsync';
import { Button, ErrorBox, Panel, when } from '../components/ui';

export function Audit() {
  const { data, error } = useAsync(() => api.audit(200));
  const [check, setCheck] = useState<VerificationResult | null>(null);
  const [checkError, setCheckError] = useState<string | null>(null);

  async function verify() {
    setCheckError(null);
    try {
      setCheck(await api.verifyAudit());
    } catch (err) {
      setCheckError(err instanceof Error ? err.message : String(err));
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-end justify-between">
        <div>
          <h1 className="text-xl font-semibold">Audit trail</h1>
          <p className="mt-1 text-sm text-ink-300">Append-only, SHA-256 hash-chained, stored in SQL Server.</p>
        </div>
        <Button variant="ghost" onClick={verify}>Verify chain</Button>
      </div>

      {checkError && <ErrorBox message={checkError} />}
      {check && (
        <div className={`rounded border px-3 py-2 text-sm ${check.valid
          ? 'border-signal/40 bg-signal/10 text-signal' : 'border-alarm/40 bg-alarm/10 text-alarm'}`}>
          {check.valid
            ? `Chain intact: ${check.entriesChecked} entries recomputed from genesis.`
            : `Tampering detected at seq ${check.firstBrokenSeq}: ${check.message}`}
        </div>
      )}
      {error && <ErrorBox message={error} />}

      <Panel title="Latest events">
        <table className="w-full text-left text-sm">
          <thead className="text-xs text-ink-500">
            <tr>
              <th className="pb-2 font-normal">Seq</th>
              <th className="pb-2 font-normal">Event</th>
              <th className="pb-2 font-normal">Entity</th>
              <th className="pb-2 font-normal">Actor</th>
              <th className="pb-2 font-normal">Time</th>
              <th className="pb-2 font-normal">Hash</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-ink-700">
            {data?.map((e) => (
              <tr key={e.seq} title={e.payloadJson}>
                <td className="tabular py-1.5 font-mono text-xs text-ink-500">{e.seq}</td>
                <td className="py-1.5 font-mono text-xs">{e.eventType}</td>
                <td className="py-1.5 font-mono text-xs text-ink-300">{e.entityType}:{e.entityId}</td>
                <td className="py-1.5 font-mono text-xs">{e.actor}</td>
                <td className="py-1.5 text-xs text-ink-300">{when(e.eventTime)}</td>
                <td className="py-1.5 font-mono text-xs text-ink-500">{e.contentHash.slice(0, 12)}…</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Panel>
    </div>
  );
}
