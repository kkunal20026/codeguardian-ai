import { useState } from 'react';
import { api } from '../api';
import type { VerificationResult } from '../types';
import { useAsync } from '../useAsync';
import { Icon } from '../components/icons';
import { Button, ErrorBox, PageHeader, Panel, th, when } from '../components/ui';

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
    <div className="space-y-6">
      <PageHeader eyebrow="Audit" title="Audit trail"
                  subtitle="Every decision, prompt and model output. Append-only, SHA-256 hash-chained, and protected by database triggers in SQL Server."
                  action={<Button variant="ghost" onClick={verify}><Icon.Chain className="h-4 w-4" /> Verify chain</Button>} />

      {checkError && <ErrorBox message={checkError} />}
      {check && (
        <div className={`rounded-xl border px-4 py-3 text-sm font-medium ${check.valid
          ? 'border-signal/40 bg-signal/10 text-signal' : 'border-alarm/40 bg-alarm/10 text-alarm'}`}>
          {check.valid
            ? `Chain intact: ${check.entriesChecked} entries recomputed from genesis.`
            : `Tampering detected at seq ${check.firstBrokenSeq}: ${check.message}`}
        </div>
      )}
      {error && <ErrorBox message={error} />}

      <Panel title="Latest events">
        <table className="w-full text-left text-sm">
          <thead>
            <tr>
              <th className={th}>Seq</th>
              <th className={th}>Event</th>
              <th className={th}>Entity</th>
              <th className={th}>Actor</th>
              <th className={th}>Time</th>
              <th className={th}>Hash</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-ink-700">
            {data?.map((e) => (
              <tr key={e.seq} title={e.payloadJson}>
                <td className="tabular py-2.5 font-mono text-xs text-ink-500">{e.seq}</td>
                <td className="py-2.5"><span className="rounded-md bg-ink-800 px-2 py-0.5 font-mono text-xs font-medium text-ink-100">{e.eventType}</span></td>
                <td className="py-2.5 font-mono text-xs text-ink-300">{e.entityType}:{e.entityId}</td>
                <td className="py-2.5 font-mono text-xs">{e.actor}</td>
                <td className="py-2.5 text-xs text-ink-300">{when(e.eventTime)}</td>
                <td className="py-2.5 font-mono text-xs text-ink-500">{e.contentHash.slice(0, 12)}…</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Panel>
    </div>
  );
}
