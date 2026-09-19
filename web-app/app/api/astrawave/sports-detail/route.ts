import { NextRequest, NextResponse } from 'next/server';

export const dynamic = 'force-dynamic';

function clean(value: unknown): string | undefined {
  const text = String(value ?? '').trim();
  return text || undefined;
}

function scoreNumber(value: unknown): number | undefined {
  const n = Number(value);
  return Number.isFinite(n) ? n : undefined;
}

export async function GET(request: NextRequest) {
  const raw = request.nextUrl.searchParams.get('eventId')?.trim() || '';
  const match = raw.match(/^espn:([^:]+):([^:]+):(.+)$/);
  if (!match) {
    return NextResponse.json({
      eventId: raw,
      source: 'unsupported',
      lines: [],
      leaders: [],
      teamStats: [],
      standings: [],
    });
  }

  const [, sport, league, eventId] = match;
  const url = `https://site.api.espn.com/apis/site/v2/sports/${encodeURIComponent(sport)}/${encodeURIComponent(league)}/summary?event=${encodeURIComponent(eventId)}`;

  try {
    const response = await fetch(url, { next: { revalidate: 30 } });
    if (!response.ok) {
      return NextResponse.json({ error: `ESPN summary ${response.status}` }, { status: 502 });
    }

    const body = await response.json() as any;
    const competition = body.header?.competitions?.[0] || {};
    const competitors = competition.competitors || [];

    const lines = competitors.map((team: any) => ({
      team: clean(team.team?.displayName) || clean(team.team?.shortDisplayName) || 'Team',
      abbreviation: clean(team.team?.abbreviation),
      score: scoreNumber(team.score),
      homeAway: clean(team.homeAway),
      periods: (team.linescores || []).map((line: any, index: number) => ({
        label: clean(line.period) || String(index + 1),
        value: scoreNumber(line.value ?? line.displayValue),
      })),
    }));

    const teamStats = (body.boxscore?.teams || []).flatMap((entry: any) => {
      const team = clean(entry.team?.displayName) || clean(entry.team?.abbreviation) || 'Team';
      return (entry.statistics || []).slice(0, 10).map((stat: any) => ({
        team,
        name: clean(stat.label) || clean(stat.name) || 'Stat',
        value: clean(stat.displayValue) || clean(stat.value) || '',
      }));
    });

    const leaders = (body.leaders || []).flatMap((group: any) =>
      (group.leaders || []).slice(0, 4).map((leader: any) => ({
        category: clean(group.displayName) || clean(group.name) || 'Leaders',
        athlete: clean(leader.athlete?.displayName) || clean(leader.athlete?.shortName) || 'Player',
        team: clean(leader.team?.abbreviation) || clean(leader.team?.displayName),
        value: clean(leader.displayValue) || clean(leader.value) || '',
      })),
    ).slice(0, 12);

    const standings = (body.standings?.groups || body.standings || []).flatMap((group: any) =>
      (group.standings || group.entries || []).slice(0, 10).map((entry: any) => ({
        team: clean(entry.team?.displayName) || clean(entry.team?.abbreviation) || 'Team',
        summary: clean(entry.stats?.map((s: any) => s.displayValue).filter(Boolean).join(' • '))
          || clean(entry.summary)
          || '',
      })),
    ).slice(0, 20);

    return NextResponse.json({
      eventId: raw,
      source: 'ESPN',
      status: clean(body.header?.competitions?.[0]?.status?.type?.description),
      statusDetail: clean(body.header?.competitions?.[0]?.status?.type?.shortDetail),
      venue: clean(body.gameInfo?.venue?.fullName),
      lines,
      teamStats,
      leaders,
      standings,
    });
  } catch (error) {
    return NextResponse.json({ error: String(error) }, { status: 500 });
  }
}
