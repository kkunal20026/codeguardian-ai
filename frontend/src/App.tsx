import { Layout } from './components/Layout';
import { usePath } from './router';
import { Overview } from './pages/Overview';
import { Findings } from './pages/Findings';
import { Proposals } from './pages/Proposals';
import { ProposalPage } from './pages/ProposalPage';
import { Audit } from './pages/Audit';
import { Empty } from './components/ui';

export function App() {
  const path = usePath();
  const proposalMatch = /^\/proposals\/(\d+)$/.exec(path);

  let page;
  if (path === '/') page = <Overview />;
  else if (path === '/findings') page = <Findings />;
  else if (path === '/proposals') page = <Proposals />;
  else if (proposalMatch) page = <ProposalPage id={Number(proposalMatch[1])} />;
  else if (path === '/audit') page = <Audit />;
  else page = <Empty>Page not found.</Empty>;

  return <Layout path={path}>{page}</Layout>;
}
