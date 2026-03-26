import { Network } from 'lucide-react';

export default function NwTopologyPage() {
  return (
    <div className="flex flex-col items-center justify-center h-full gap-3 text-slate-500 bg-slate-950">
      <Network className="size-10 text-slate-700" />
      <p className="text-sm font-medium tracking-wide">NW Topology Monitor</p>
      <p className="text-xs text-slate-600">Coming Soon</p>
    </div>
  );
}
