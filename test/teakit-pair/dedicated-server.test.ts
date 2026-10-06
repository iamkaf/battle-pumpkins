import { Capability, Readiness, describe, expect, test } from "@teakit/test";
import type { TeaKitTestContext } from "@teakit/test";

// Runs only in a production client and dedicated-server pair: `just pair <node>`.

const PUMPKIN = "battlepumpkins:battle_pumpkin";
// Matched by title: the 1.21.11 horizontal jar carries loader-suffixed copies of the screen class.
const CARVE_SCREEN = "Battle Pumpkin";

describe.configure({
  timeout: "5m",
  readiness: [Readiness.World, Readiness.Player],
  capabilities: [
    Capability.ClientScreens,
    Capability.ClientScreenshot,
    Capability.PlayerInteractions,
    Capability.RuntimeTiming,
    Capability.ServerCommands,
    Capability.WorldEntities,
  ],
});

describe("Battle Pumpkins on a dedicated server", () => {
  test("a joined player catches, carves, and lights a pumpkin", async (ctx) => {
    if (!(await ctx.session.info()).paired) throw new Error("This test needs a client and dedicated-server pair");

    // A sky platform away from terrain. Creative until the platform exists, so the player cannot fall while its chunks load. A fill into a chunk that is
    // still loading fails quietly, so retry until the grass is really there.
    await ctx.commands.batch(["/gamemode creative @s", "/clear @s", "/tp @s 0.5 101 -2.5 0 20"]);
    await ctx.commands.batch(["/gamerule spawn_mobs false", "/time set midnight", "/weather clear", `/kill @e[type=${PUMPKIN}]`]);
    await eventually(ctx, "execute if block 0 100 0 minecraft:grass_block", 30_000, async () => {
      await ctx.commands.batch(["/fill -4 100 -4 4 100 4 minecraft:grass_block", "/fill -4 101 -4 4 105 4 minecraft:air"]);
    });
    await ctx.commands.batch([
      "/tp @s 0.5 101 -2.5 0 20",
      "/gamemode survival @s",
      `/summon ${PUMPKIN} 0.5 101 0.5 {NoAI:1b,Rotation:[180f,0f]}`,
    ]);
    let pumpkin = null;
    for (let tries = 0; pumpkin == null && tries < 20; tries++) {
      await ctx.runtime.wait(500);
      pumpkin = await ctx.entities.nearest(PUMPKIN, { x: 0.5, y: 101, z: 0.5 });
    }
    if (pumpkin == null) throw new Error("The staged Battle Pumpkin did not spawn");

    // The server catches it and opens the carving screen on the client, once the client has the pumpkin.
    await untilClientSees(ctx, () => ctx.player.useItemOnEntity(pumpkin, "minecraft:shears"));
    const screen = await ctx.client.waitForScreen(CARVE_SCREEN, { timeoutMs: 10_000 });
    await eventually(ctx, `execute if data entity @e[type=${PUMPKIN},limit=1] Owner`);

    // The client carves two pixels and the server stores exactly those.
    await screen.widgets().activate({ label: "Clear" });
    const done = screen.widgets().all().find((widget) => widget.label === "Done");
    if (done == null) throw new Error("The carving screen has no Done button");
    for (const [x, y] of [[0, 0], [15, 15]]) {
      await ctx.client.click({ x: done.x - 116 + 9 + x * 6 + 3, y: done.y - 96 + 19 + y * 6 + 3, button: 0 });
    }
    await screen.widgets().activate({ label: "Done" });
    await eventually(ctx, `execute if entity @e[type=${PUMPKIN},nbt={Face0:1L,Face1:0L,Face2:0L,Face3:-9223372036854775808L}]`);

    await ctx.player.useItemOnEntity(pumpkin, "minecraft:torch");
    await eventually(ctx, `execute if entity @e[type=${PUMPKIN},nbt={Light:"torch"}]`);
    await ctx.client.waitForFrames(10);
    await ctx.client.screenshot("battle-pumpkins-dedicated-server", { hideOverlay: true, hideWindowDecoration: true });
    expect(true).toBe(true);
  });
});

async function untilClientSees(ctx: TeaKitTestContext, interact: () => Promise<unknown>): Promise<void> {
  const deadline = Date.now() + 10_000;
  for (;;) {
    try {
      await interact();
      return;
    } catch (error) {
      if (Date.now() > deadline || !String(error).includes("No live client entity")) throw error;
      await ctx.runtime.wait(500);
    }
  }
}

/** Poll a server command until it passes; client actions reach the server a tick or more later. */
async function eventually(ctx: TeaKitTestContext, command: string, timeoutMs = 5_000, retry?: () => Promise<void>): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    await retry?.();
    if (((await ctx.commands.run(`/${command}`)) as { result?: number }).result) return;
    await ctx.runtime.wait(250);
  }
  throw new Error(`Timed out waiting for: ${command}`);
}
