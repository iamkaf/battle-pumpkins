package com.iamkaf.battlepumpkins.client;

import com.iamkaf.battlepumpkins.network.BattlePumpkinsNetwork;
import com.iamkaf.battlepumpkins.pumpkin.BattlePumpkin;
import com.iamkaf.battlepumpkins.pumpkin.Face;
import com.iamkaf.battlepumpkins.pumpkin.Light;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=26.1
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? if <26.1
/*import net.minecraft.client.gui.GuiGraphics;*/
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Carves a Battle Pumpkin's face pixel by pixel over the vanilla pumpkin side. Left-drag carves, right-drag fills.
 * Done sends the whole face; Take out drops the light right away.
 */
public class CarveScreen extends Screen {
    private static final Identifier PUMPKIN_SIDE = Identifier.withDefaultNamespace("textures/block/pumpkin_side.png");
    private static final int WIDTH = 176;
    private static final int HEIGHT = 134;
    private static final int CELL = 6;
    private static final int GRID_X = 9;
    private static final int GRID_Y = 19;
    private static final int GRID_SIZE = Face.SIZE * CELL;
    private static final int COLUMN_X = 116;
    private static final int COLUMN_WIDTH = 52;
    private static final int PREVIEW_SCALE = 2;
    private static final double EDIT_DISTANCE_SQR = 64.0;

    private final BattlePumpkin pumpkin;
    private final Face original;
    private Face face;
    /** Whether the current drag carves or fills; null when no drag is under way. */
    private @Nullable Boolean painting;
    private @Nullable Button takeOut;
    private int left;
    private int top;

    private CarveScreen(BattlePumpkin pumpkin) {
        super(pumpkin.getDisplayName());
        this.pumpkin = pumpkin;
        this.original = pumpkin.face();
        this.face = original;
    }

    public static void open(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getEntity(entityId) instanceof BattlePumpkin pumpkin) {
            //? if >=26.2
            minecraft.gui.setScreen(new CarveScreen(pumpkin));
            //? if <26.2
            /*minecraft.setScreen(new CarveScreen(pumpkin));*/
        }
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        int x = left + COLUMN_X;
        takeOut = addRenderableWidget(Button.builder(Component.translatable("battlepumpkins.screen.take_out"),
                        button -> BattlePumpkinsNetwork.CHANNEL.sendToServer(new BattlePumpkinsNetwork.TakeOutLight(pumpkin.getId())))
                .bounds(x, top + 54, COLUMN_WIDTH, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("battlepumpkins.screen.clear"), button -> face = Face.BLANK)
                .bounds(x, top + 75, COLUMN_WIDTH, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(x, top + 96, COLUMN_WIDTH, 20)
                .build());
    }

    @Override
    public void tick() {
        if (!pumpkin.isAlive() || pumpkin.isKnockedOut() || minecraft == null || minecraft.player == null
                || pumpkin.distanceToSqr(minecraft.player) > EDIT_DISTANCE_SQR) {
            onClose();
            return;
        }
        if (takeOut != null) {
            takeOut.active = pumpkin.light().glows();
        }
    }

    @Override
    public void onClose() {
        if (!face.equals(original)) {
            BattlePumpkinsNetwork.CHANNEL.sendToServer(new BattlePumpkinsNetwork.Carve(pumpkin.getId(), face));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (cellAt(event.x(), event.y()) != null && (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT)) {
            painting = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
            paint(event.x(), event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (painting != null) {
            paint(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        painting = null;
        return super.mouseReleased(event);
    }

    private void paint(double mouseX, double mouseY) {
        int[] cell = cellAt(mouseX, mouseY);
        if (cell != null && painting != null) {
            face = face.with(cell[0], cell[1], painting);
        }
    }

    private int @Nullable [] cellAt(double mouseX, double mouseY) {
        int x = (int) Math.floor((mouseX - left - GRID_X) / CELL);
        int y = (int) Math.floor((mouseY - top - GRID_Y) / CELL);
        return x >= 0 && y >= 0 && x < Face.SIZE && y < Face.SIZE ? new int[]{x, y} : null;
    }

    //? if >=26.1 {
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(new Canvas(graphics));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawFace(new Canvas(graphics));
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(new Canvas(graphics));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawFace(new Canvas(graphics));
    }
    *///?}

    /** A flat vanilla container panel with recessed wells for the grid and the preview. */
    private void drawPanel(Canvas canvas) {
        canvas.fill(left, top, left + WIDTH, top + HEIGHT, 0xFF000000);
        canvas.fill(left + 1, top + 1, left + WIDTH - 1, top + HEIGHT - 1, 0xFFFFFFFF);
        canvas.fill(left + 2, top + 2, left + WIDTH - 1, top + HEIGHT - 1, 0xFF555555);
        canvas.fill(left + 2, top + 2, left + WIDTH - 2, top + HEIGHT - 2, 0xFFC6C6C6);
        well(canvas, left + GRID_X - 1, top + GRID_Y - 1, GRID_SIZE + 2);
        well(canvas, previewX() - 1, previewY() - 1, Face.SIZE * PREVIEW_SCALE + 2);
        canvas.text(font, title, left + 8, top + 7, 0xFF404040);
        canvas.text(font, Component.translatable("battlepumpkins.screen.hint"), left + 8, top + GRID_Y + GRID_SIZE + 5, 0xFF707070);
    }

    private static void well(Canvas canvas, int x, int y, int size) {
        canvas.fill(x, y, x + size, y + size, 0xFF373737);
        canvas.fill(x + 1, y + 1, x + size, y + size, 0xFFFFFFFF);
        canvas.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF8B8B8B);
    }

    private void drawFace(Canvas canvas) {
        Light glow = pumpkin.light();
        drawPumpkin(canvas, left + GRID_X, top + GRID_Y, CELL, glow);
        for (int i = 1; i < Face.SIZE; i++) {
            canvas.fill(left + GRID_X + i * CELL, top + GRID_Y, left + GRID_X + i * CELL + 1, top + GRID_Y + GRID_SIZE, 0x22000000);
            canvas.fill(left + GRID_X, top + GRID_Y + i * CELL, left + GRID_X + GRID_SIZE, top + GRID_Y + i * CELL + 1, 0x22000000);
        }
        drawPumpkin(canvas, previewX(), previewY(), PREVIEW_SCALE, glow);
    }

    private void drawPumpkin(Canvas canvas, int x, int y, int scale, Light glow) {
        int size = Face.SIZE * scale;
        canvas.texture(PUMPKIN_SIDE, x, y, size);
        for (int py = 0; py < Face.SIZE; py++) {
            for (int px = 0; px < Face.SIZE; px++) {
                if (face.carved(px, py)) {
                    int color = face.surrounded(px, py) ? glow.innerColor() : glow.edgeColor();
                    canvas.fill(x + px * scale, y + py * scale, x + (px + 1) * scale, y + (py + 1) * scale, color);
                }
            }
        }
    }

    private int previewX() {
        return left + COLUMN_X + (COLUMN_WIDTH - Face.SIZE * PREVIEW_SCALE) / 2;
    }

    private int previewY() {
        return top + GRID_Y;
    }

    /** The few drawing calls this screen makes, over the graphics type each Minecraft version provides. */
    private record Canvas(
            //? if >=26.1
            GuiGraphicsExtractor graphics
            //? if <26.1
            /*GuiGraphics graphics*/
    ) {
        void fill(int x0, int y0, int x1, int y1, int color) {
            graphics.fill(x0, y0, x1, y1, color);
        }

        void texture(Identifier texture, int x, int y, int size) {
            graphics.blit(texture, x, y, x + size, y + size, 0.0F, 1.0F, 0.0F, 1.0F);
        }

        void text(Font font, Component text, int x, int y, int color) {
            //? if >=26.1
            graphics.text(font, text, x, y, color, false);
            //? if <26.1
            /*graphics.drawString(font, text, x, y, color, false);*/
        }
    }
}
