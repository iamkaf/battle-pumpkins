import { Capability, Readiness, describe, expect, test } from "@teakit/test";
import type { ClientScreen, EntityRef, TeaKitTestContext, Vec3 } from "@teakit/test";

const PUMPKIN = "battlepumpkins:battle_pumpkin";
const CARVE_SCREEN = "com.iamkaf.battlepumpkins.client.CarveScreen";
const SELECTOR = `@e[type=${PUMPKIN},limit=1,sort=nearest]`;
// The vanilla carved pumpkin face, which a pumpkin gets when it is caught.
const VANILLA_FACE = "Face0:1040336118127525888L,Face1:4337035126785973880L,Face2:9222316489202728960L,Face3:138964696L";
const SNEAK_KEY = 340; // GLFW left shift, the default sneak key
const CONFIG_SCREEN = "com.iamkaf.konfig.impl.v1.client.screen.KonfigConfigScreen";

describe.configure({
  timeout: "5m",
  readiness: [Readiness.World, Readiness.Player],
  capabilities: [
    Capability.ClientInput,
    Capability.ClientScreens,
    Capability.ClientScreenshot,
    Capability.PlayerInteractions,
    Capability.RuntimeTiming,
    Capability.ServerCommands,
    Capability.WorldEntities,
  ],
});

describe("Battle Pumpkins", () => {
  test("shears catch a wild pumpkin, carve its face, and open the carving screen", async (ctx) => {
    const pumpkin = await stage(ctx);
    await ctx.commands.assert(`/execute if entity @e[type=${PUMPKIN},nbt={Face0:0L}]`);

    await ctx.player.useItemOnEntity(pumpkin, "minecraft:shears");
    const screen = await ctx.client.waitForScreen(CARVE_SCREEN, { timeoutMs: 5_000 });
    await ctx.client.screenshot("battle-pumpkins-carving-screen", { hideWindowDecoration: true });

    await ctx.commands.assert(`/execute if data entity ${SELECTOR} Owner`);
    await ctx.commands.assert(`/execute if entity @e[type=${PUMPKIN},nbt={${VANILLA_FACE}}]`);

    // Clear the face, carve the top-left and bottom-right pixels, and Done saves exactly those two.
    await screen.widgets().activate({ label: "Clear" });
    const done = screen.widgets().all().find((widget) => widget.label === "Done");
    if (done == null) {
      throw new Error("The carving screen has no Done button");
    }
    const left = done.x - 116;
    const top = done.y - 96;
    for (const [x, y] of [[0, 0], [15, 15]]) {
      await ctx.client.click({ x: left + 9 + x * 6 + 3, y: top + 19 + y * 6 + 3, button: 0 });
    }
    await ctx.client.screenshot("battle-pumpkins-carved-corners", { hideWindowDecoration: true });
    await screen.widgets().activate({ label: "Done" });
    await ctx.runtime.wait(500);
    await ctx.commands.assert(`/execute if entity @e[type=${PUMPKIN},nbt={Face0:1L,Face1:0L,Face2:0L,Face3:-9223372036854775808L}]`);
  });

  test("a torch lights a caught pumpkin and another light swaps it out", async (ctx) => {
    const pumpkin = await caught(ctx);

    await ctx.player.useItemOnEntity(pumpkin, "minecraft:torch");
    await eventually(ctx, `/execute if entity @e[type=${PUMPKIN},nbt={Light:"torch"}]`);
    await ctx.commands.run("/tp @s 0.5 71 -3.5 0 12");
    await ctx.client.waitForFrames(10);
    await ctx.client.screenshot("battle-pumpkins-lit-at-night", { hideOverlay: true, hideWindowDecoration: true });

    await ctx.commands.run("/tp @s 0.5 71 -2.5 0 20");
    await ctx.player.useItemOnEntity(pumpkin, "minecraft:soul_torch");
    await eventually(ctx, `/execute if entity @e[type=${PUMPKIN},nbt={Light:"soul"}]`);
    // Swapping lights pops the previous torch out.
    await eventually(ctx, "/execute if entity @e[type=minecraft:item,nbt={Item:{id:\"minecraft:torch\"}}]");
  });

  test("an owned pumpkin is knocked out instead of killed, and seeds wake it", async (ctx) => {
    const pumpkin = await caught(ctx);

    await ctx.commands.run(`/damage ${SELECTOR} 100 minecraft:generic`);
    await ctx.runtime.wait(500);
    await ctx.commands.assert(`/execute if entity @e[type=${PUMPKIN},nbt={Dormant:1b}]`);
    // Knocked out pumpkins cannot be hurt any further.
    await ctx.commands.run(`/damage ${SELECTOR} 100 minecraft:generic`);
    await ctx.commands.assert(`/execute if entity @e[type=${PUMPKIN},nbt={Dormant:1b}]`);

    await ctx.player.useItemOnEntity(pumpkin, "minecraft:pumpkin_seeds");
    await eventually(ctx, `/execute if entity @e[type=${PUMPKIN},nbt={Dormant:0b}]`);

    await ctx.commands.run(`/kill ${SELECTOR}`);
    await eventually(ctx, `/execute unless entity @e[type=${PUMPKIN}]`);
  });

  test("right-click stands a caught pumpkin up, and it fights a zombie that hurts its owner", async (ctx) => {
    const pumpkin = await caught(ctx);
    await ctx.commands.run(`/data merge entity ${SELECTOR} {NoAI:0b}`);
    await ctx.commands.assert(`/execute if entity @e[type=${PUMPKIN},nbt={Sitting:1b}]`);

    await ctx.player.useItemOnEntity(pumpkin);
    await eventually(ctx, `/execute if entity @e[type=${PUMPKIN},nbt={Sitting:0b}]`);

    await ctx.commands.run("/summon minecraft:zombie 3.5 71 3.5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"bp_target\"]}");
    await ctx.runtime.wait(500);
    await ctx.commands.run("/damage @s 1 minecraft:mob_attack by @e[tag=bp_target,limit=1]");
    // The zombie is hurt or already dead.
    await eventually(ctx, "/execute unless entity @e[tag=bp_target,nbt={Health:20.0f}]", 15_000);
    await ctx.commands.run("/kill @e[tag=bp_target]");
  });

  test("a sleeping wild pumpkin ignores a sneaking player, and wakes and hops away from one who isn't", async (ctx) => {
    await ctx.client.keyState(SNEAK_KEY, true);
    try {
      const pumpkin = await stage(ctx, "{Dormant:1b,Rotation:[180f,0f]}", "noon");
      await ctx.runtime.wait(2_000);
      await ctx.commands.assert(`/execute if entity @e[type=${PUMPKIN},nbt={Dormant:1b}]`);

      await ctx.client.keyState(SNEAK_KEY, false);
      await eventually(ctx, `/execute if entity @e[type=${PUMPKIN},nbt={Dormant:0b}]`);
      const before = await distanceToPlayer(ctx, pumpkin);
      await ctx.runtime.wait(3_000);
      expect(await distanceToPlayer(ctx, pumpkin)).toBeGreaterThan(before + 2);
    } finally {
      await ctx.client.keyState(SNEAK_KEY, false);
    }
  });

  test("sneaking and using an owned pumpkin reopens the carving screen, where Take Out drops its light", async (ctx) => {
    const pumpkin = await caught(ctx);
    await ctx.player.useItemOnEntity(pumpkin, "minecraft:torch");
    await eventually(ctx, `/execute if entity @e[type=${PUMPKIN},nbt={Light:"torch"}]`);

    await ctx.client.keyState(SNEAK_KEY, true);
    let screen: ClientScreen;
    try {
      await ctx.runtime.wait(300);
      await ctx.player.useItemOnEntity(pumpkin);
      screen = await ctx.client.waitForScreen(CARVE_SCREEN, { timeoutMs: 5_000 });
    } finally {
      await ctx.client.keyState(SNEAK_KEY, false);
    }
    await screen.widgets().activate({ label: "Take Out" });
    await eventually(ctx, `/execute if entity @e[type=${PUMPKIN},nbt={Light:"none"}]`);
    // The torch pops out beside the pumpkin, and the player may already have picked it up.
    await eventually(ctx, "/execute if entity @e[type=minecraft:item,nbt={Item:{id:\"minecraft:torch\"}}]")
      .catch(() => eventually(ctx, "/execute if items entity @s container.* minecraft:torch"));
    await screen.widgets().activate({ label: "Done" });
  });

  // TeaKit's Forge control plane stops answering after leaveWorld, so this runs on Fabric and NeoForge.
  test("the config screen explains each setting with a picture", { target: { loader: ["fabric", "neoforge"] } }, async (ctx) => {
    await ctx.client.leaveWorld();
    await ctx.client.waitForScreen("Title", { timeoutMs: 30_000 });
    let screen = await ctx.client.screen();
    await screen.widgets().activate({ label: "Mods", contains: true });
    screen = await ctx.client.waitForScreen("Mods", { timeoutMs: 5_000 });
    if (screen.widgets().all().some((widget) => widget.label === "A-Z")) {
      await screen.widgets().activate({ label: "A-Z" });
    }
    screen = await selectMod(ctx, "Battle Pumpkins");
    if ((await ctx.runtime.health()).loader === "fabric") {
      await activateModMenuConfigure(screen);
    } else {
      await screen.widgets().activate({ label: "Config", nth: 0 });
    }

    screen = await ctx.client.waitForScreen(CONFIG_SCREEN, { timeoutMs: 10_000 });
    const labels = screen.lists().entries().map((entry) => entry.label);
    expect(labels).toContain("Off-Season Spawns");
    expect(labels).toContain("Force Event");
    const entry = screen.lists().entries().find((candidate) => candidate.label === "Force Event");
    if (entry != null) {
      // Pointing at a row fills the info panel with its picture.
      await ctx.client.click({ x: entry.x + 4, y: entry.y + entry.height / 2, button: 1 });
      await ctx.client.waitForFrames(5);
    }
    await ctx.client.screenshot("battle-pumpkins-config-screen", { hideWindowDecoration: true });
  });
});

/** A flat grass stage with one wild pumpkin three blocks in front of the player; by default it holds still at night. */
async function stage(ctx: TeaKitTestContext, nbt = "{NoAI:1b,Rotation:[180f,0f]}", time = "midnight"): Promise<EntityRef> {
  await ctx.commands.batch([
    "/gamemode survival @s",
    "/gamerule spawn_mobs false",
    `/time set ${time}`,
    "/weather clear",
    `/kill @e[type=${PUMPKIN}]`,
    "/kill @e[type=minecraft:item]",
    "/fill -16 70 -16 16 70 16 minecraft:grass_block replace",
    "/fill -16 71 -16 16 76 16 minecraft:air replace",
    "/tp @s 0.5 71 -2.5 0 20",
    "/clear @s",
  ]);
  await ctx.client.closeMenus();
  // A killed pumpkin lingers through its death animation; wait so nearest() cannot pick it up.
  await ctx.runtime.wait(1_500);
  await ctx.commands.run(`/summon ${PUMPKIN} 0.5 71 0.5 ${nbt}`);
  await ctx.runtime.wait(500);
  const pumpkin = await ctx.entities.nearest(PUMPKIN, { x: 0.5, y: 71, z: 0.5 });
  if (pumpkin == null) {
    throw new Error("The staged Battle Pumpkin did not spawn");
  }
  return pumpkin;
}

async function caught(ctx: TeaKitTestContext): Promise<EntityRef> {
  const pumpkin = await stage(ctx);
  await ctx.player.useItemOnEntity(pumpkin, "minecraft:shears");
  // The server opens the carving screen once the catch lands; close it after it appears.
  await ctx.client.waitForScreen(CARVE_SCREEN, { timeoutMs: 5_000 });
  await ctx.client.closeMenus();
  await ctx.commands.assert(`/execute if data entity ${SELECTOR} Owner`);
  return pumpkin;
}

/** Interactions reach the server a tick or so after the client sends them, so poll before asserting. */
async function eventually(ctx: TeaKitTestContext, command: string, timeoutMs = 3_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const result = await ctx.commands.run(command);
    if ((result as { result?: number }).result) {
      return;
    }
    await ctx.runtime.wait(250);
  }
  await ctx.commands.assert(command);
}


async function distanceToPlayer(ctx: TeaKitTestContext, pumpkin: EntityRef): Promise<number> {
  const at = (await pumpkin.inspect()).position as Vec3;
  const player = await ctx.player.position();
  return Math.hypot(at.x - player.x, at.z - player.z);
}

// Mod lists do not reliably select an entry through activate(), so scroll to it and click until it reports selected.
async function selectMod(ctx: TeaKitTestContext, label: string): Promise<ClientScreen> {
  const startedAt = Date.now();
  while (Date.now() - startedAt < 10_000) {
    const screen = await ctx.client.screen();
    const entry = screen.lists().entries().find((candidate) => candidate.label.includes(label));
    if (entry?.selected) return screen;
    if (entry != null) {
      await ctx.client.click({ x: entry.x + entry.width / 2, y: entry.y + entry.height / 2, button: 0 });
    } else {
      await screen.scroll({ vertical: -2 });
    }
    await ctx.runtime.wait(200);
  }
  const labels = (await ctx.client.screen()).lists().entries().map((entry) => entry.label);
  throw new Error(`Timed out selecting mod list entry ${label}; visible entries: ${labels.join(", ")}`);
}

// Mod Menu labels its configure button on newer versions; older versions only expose the icon widget.
async function activateModMenuConfigure(screen: ClientScreen): Promise<void> {
  if (screen.widgets().all().some((widget) => widget.label === "Configure...")) {
    await screen.widgets().activate({ label: "Configure..." });
    return;
  }
  await screen.widgets().activate({ widgetClass: "com.terraformersmc.modmenu.gui.widget.LegacyTexturedButtonWidget", nth: 1 });
}
