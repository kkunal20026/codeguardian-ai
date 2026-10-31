export function DiffView({ diff }: { diff: string }) {
  if (!diff.trim()) {
    return <div className="text-sm text-ink-500">No patch. The agent could not produce a safe change.</div>;
  }
  return (
    <pre className="overflow-x-auto rounded-xl border border-ink-700 bg-ink-950 py-3 font-mono text-[12px] leading-5">
      {diff.split('\n').map((line, i) => {
        const cls = line.startsWith('+++') || line.startsWith('---')
          ? 'text-ink-300'
          : line.startsWith('@@')
            ? 'text-info'
            : line.startsWith('+')
              ? 'bg-signal/10 text-signal'
              : line.startsWith('-')
                ? 'bg-alarm/10 text-alarm'
                : 'text-ink-300';
        return (
          <div key={i} className={`px-3 ${cls}`}>
            {line || ' '}
          </div>
        );
      })}
    </pre>
  );
}
