package ravenweave.client.module.modules.combat;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings.GameType;
import net.weavemc.loader.api.event.RenderWorldEvent;
import net.weavemc.loader.api.event.SubscribeEvent;
import org.lwjgl.input.Mouse;
import ravenweave.client.event.GameLoopEvent;
import ravenweave.client.event.LookEvent;
import ravenweave.client.event.MoveInputEvent;
import ravenweave.client.event.UpdateEvent;
import ravenweave.client.module.Module;
import ravenweave.client.module.modules.aycy.optimalaim.OptimalAim;
import ravenweave.client.module.modules.client.Targets;
import ravenweave.client.module.setting.impl.ComboSetting;
import ravenweave.client.module.setting.impl.DoubleSliderSetting;
import ravenweave.client.module.setting.impl.SliderSetting;
import ravenweave.client.module.setting.impl.TickSetting;
import ravenweave.client.utils.CoolDown;
import ravenweave.client.utils.Utils;

import java.awt.*;

//todo change the clicking system
public class KillAura extends Module {

    private EntityPlayer target;

    public static SliderSetting reach;
    private final DoubleSliderSetting cps;
    private final TickSetting disableWhenFlying, mouseDown, onlySurvival, fixMovement;
    public static ComboSetting<RotationMode> rotationMode;
    private final CoolDown coolDown = new CoolDown(1);
    private boolean leftDown, leftn, locked;
    private long leftDownTime, leftUpTime, leftk, leftl;
    public static float yaw, pitch, prevYaw, prevPitch;
    private double leftm;

    public KillAura() {
        super("KillAura", ModuleCategory.combat);
        this.registerSetting(reach = new SliderSetting("Reach (Blocks)", 3.3, 3, 6, 0.05));
        this.registerSetting(cps = new DoubleSliderSetting("Left CPS", 9, 13, 1, 60, 0.5));
        this.registerSetting(onlySurvival = new TickSetting("Only Survival", false));
        this.registerSetting(disableWhenFlying = new TickSetting("Disable when flying", true));
        this.registerSetting(mouseDown = new TickSetting("Mouse Down", false));
        this.registerSetting(fixMovement = new TickSetting("Movement Fix", true));
        this.registerSetting(rotationMode = new ComboSetting<>("RotationMode", RotationMode.DEFAULT));
    }

    @SubscribeEvent
    public void onGameLoop(GameLoopEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;
        try {
            Mouse.poll();
            EntityPlayer pTarget = Targets.getTarget();
            if (    pTarget == null
                    || mc.currentScreen != null
                    || !(!onlySurvival.isToggled() || (mc.playerController.getCurrentGameType() == GameType.SURVIVAL))
                    || !coolDown.hasFinished()
                    || !(!mouseDown.isToggled() || Mouse.isButtonDown(0))
                    || !(!disableWhenFlying.isToggled() || !mc.thePlayer.capabilities.isFlying)) {
                target = null;
                rotate(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
                return;
            }
            target = pTarget;
            this.leftClickExecute(mc.gameSettings.keyBindAttack.getKeyCode());
            float[] rotations;
            if (rotationMode.getMode() == RotationMode.DEFAULT)
                rotations = Utils.Player.getTargetRotations(target, 0);
            else if (rotationMode.getMode() == RotationMode.OPTIMAL_REACH) {
                Vec3 pos = OptimalAim.getOptimalAim();
                if (pos == null) return;
                rotations = Utils.Player.getRotations(pos.xCoord, pos.yCoord, pos.zCoord);
            } else {
                rotations = new float[] {mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
            }
            locked = false;
            rotate(rotations[0], rotations[1]);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @SubscribeEvent
    public void onUpdate(UpdateEvent e) {
        if(e.isPre()) {
            if(!Utils.Player.isPlayerInGame() || locked) {
                return;
            }

            float[] currentRots = new float[]{yaw,pitch};
            float[] prevRots = new float[]{prevYaw,prevPitch};
            float[] gcdPatch = getPatchedRots(currentRots,prevRots);

            e.setYaw(gcdPatch[0]);
            e.setPitch(gcdPatch[1]);

            mc.thePlayer.renderYawOffset = gcdPatch[0];
            mc.thePlayer.rotationYawHead = gcdPatch[0];

            prevYaw = e.getYaw();
            prevPitch = e.getPitch();
        }
    }

    @SubscribeEvent
    public void renderWorldLast(RenderWorldEvent renderWorldEvent) {
        if (target == null || !Utils.Player.isPlayerInGame())
            return;
        int red = (int) (((20 - target.getHealth()) * 13) > 255 ? 255 : (20 - target.getHealth()) * 13);
        int green = 255 - red;
        final int rgb = new Color(red, green, 0).getRGB();
        Utils.HUD.drawBoxAroundEntity(target, 2, 0, 0, rgb, false);
    }

    public void rotate(float yaw, float pitch) {
        KillAura.yaw = yaw;
        KillAura.pitch = pitch;
    }

    private double MouseSens() {
        final float sens = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        final float pow = sens * sens * sens * 8.0F;
        return pow * 0.15D;
    }

    private float[] getPatchedRots(final float[] currentRots, final float[] prevRots) {
        final float yawDif = currentRots[0] - prevRots[0];
        final float pitchDif = currentRots[1] - prevRots[1];
        final double gcd = MouseSens();

        currentRots[0] -= (float) (yawDif % gcd);
        currentRots[1] -= (float) (pitchDif % gcd);
        return currentRots;
    }

    @SubscribeEvent
    public void onMoveInput(MoveInputEvent e) {
        if(!fixMovement.isToggled() || locked || !Utils.Player.isPlayerInGame()) return;
        e.setYaw(yaw);
    }

    @SubscribeEvent
    public void lookEvent(LookEvent e) {
        if(locked || !Utils.Player.isPlayerInGame()) return;
        e.setPrevYaw(prevYaw);
        e.setPrevPitch(prevPitch);
        e.setYaw(yaw);
        e.setPitch(pitch);
    }

    /**
     * TODO: Recode whatever is below this and finish autoblock
     * will do it in the near future when i get time to open my intellij
     */

    public void leftClickExecute(int key) {
        if (!Utils.Player.isPlayerInGame()) return;
        if ((this.leftUpTime > 0L) && (this.leftDownTime > 0L)) {
            if ((System.currentTimeMillis() > this.leftUpTime) && leftDown) {
                if(mc.thePlayer.isUsingItem())
                    mc.thePlayer.stopUsingItem();
                KeyBinding.onTick(key);
                this.genLeftTimings();
                Utils.Client.setMouseButtonState(0, true);
                leftDown = false;
            } else if (System.currentTimeMillis() > this.leftDownTime) {
                if(Mouse.isButtonDown(1))
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                leftDown = true;
                Utils.Client.setMouseButtonState(0, false);
            }
        } else
            this.genLeftTimings();

    }

    public void genLeftTimings() {
        double clickSpeed = Utils.Client.ranModuleVal(cps, Utils.Java.rand()) + (0.4D * Utils.Java.rand().nextDouble());
        long delay = (int) Math.round(1000.0D / clickSpeed);
        if (System.currentTimeMillis() > this.leftk) {
            if (!this.leftn && (Utils.Java.rand().nextInt(100) >= 85)) {
                this.leftn = true;
                this.leftm = 1.1D + (Utils.Java.rand().nextDouble() * 0.15D);
            } else
                this.leftn = false;

            this.leftk = System.currentTimeMillis() + 500L + Utils.Java.rand().nextInt(1500);
        }

        if (this.leftn)
            delay = (long) (delay * this.leftm);

        if (System.currentTimeMillis() > this.leftl) {
            if (Utils.Java.rand().nextInt(100) >= 80)
                delay += 50L + Utils.Java.rand().nextInt(100);

            this.leftl = System.currentTimeMillis() + 500L + Utils.Java.rand().nextInt(1500);
        }

        this.leftUpTime = System.currentTimeMillis() + delay;
        this.leftDownTime = (System.currentTimeMillis() + (delay / 2L)) - Utils.Java.rand().nextInt(10);
    }
    public enum RotationMode{
        NONE,
        DEFAULT,
        OPTIMAL_REACH
    }
}
