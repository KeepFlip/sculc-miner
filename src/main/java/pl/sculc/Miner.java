package pl.sculc;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chodzi tam i z powrotem miedzy pos1 a pos2 (trasa) i kopie wybrane bloki
 * w terenie kop1-kop2 (to, co jest w zasiegu z trasy).
 */
public class Miner {
    /** Trasa chodzenia lewo/prawo. */
    public BlockPos pos1, pos2;
    /** Teren do kopania. */
    public BlockPos kop1, kop2;
    public final Set<Block> blocks = new LinkedHashSet<>();
    public boolean running = false;

    private static final double REACH = 4.0;
    private static final long MAX_VOLUME = 300_000L;
    /** Kucanie podczas pracy (wolniejsze chodzenie, ale nie spadniesz z krawedzi). */
    private static final boolean SNEAK = false;

    private BlockPos current;
    private boolean headingToPos2;
    private boolean minedThisLeg;
    private int idleLegs;
    private int tickCounter;
    private int stuckTicks;
    private int idleTicks;
    private Vec3d lastPos = Vec3d.ZERO;

    public String blockNames() {
        if (blocks.isEmpty()) return "(brak)";
        return blocks.stream().map(b -> Registries.BLOCK.getId(b).toString()).collect(Collectors.joining(", "));
    }

    public void start(MinecraftClient mc) {
        if (mc.player == null) return;
        if (pos1 == null || pos2 == null) {
            msg(mc, "Ustaw trase: /sculc-ustaw-pozycja-1 i /sculc-ustaw-pozycja-2");
            return;
        }
        if (kop1 == null || kop2 == null) {
            msg(mc, "Ustaw teren kopania: /sculc-kop-1 i /sculc-kop-2");
            return;
        }
        if (blocks.isEmpty()) {
            msg(mc, "Wybierz blok: /sculc-block <blok>");
            return;
        }
        long vol = (long) (Math.abs(kop1.getX() - kop2.getX()) + 1)
                * (Math.abs(kop1.getY() - kop2.getY()) + 1)
                * (Math.abs(kop1.getZ() - kop2.getZ()) + 1);
        if (vol > MAX_VOLUME) {
            msg(mc, "Teren kopania za duzy (" + vol + " blokow, max " + MAX_VOLUME + ").");
            return;
        }
        current = null;
        minedThisLeg = true;
        idleLegs = 0;
        tickCounter = 0;
        stuckTicks = 0;
        idleTicks = 0;
        // zacznij od isc do dalszego konca trasy
        Vec3d pp = mc.player.getPos();
        headingToPos2 = Vec3d.ofCenter(pos2).squaredDistanceTo(pp.x, Vec3d.ofCenter(pos2).y, pp.z)
                >= Vec3d.ofCenter(pos1).squaredDistanceTo(pp.x, Vec3d.ofCenter(pos1).y, pp.z);
        running = true;
        msg(mc, "START. Kopie: " + blockNames());
    }

    public void stop(MinecraftClient mc, String why) {
        boolean was = running;
        running = false;
        current = null;
        if (mc != null) {
            releaseKeys(mc);
            if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
            if (was && mc.player != null) msg(mc, why);
        }
    }

    public void tick(MinecraftClient mc) {
        if (!running) return;
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (p == null || w == null || mc.interactionManager == null) {
            running = false;
            return;
        }
        if (mc.currentScreen != null) {
            releaseKeys(mc);
            return;
        }

        if (SNEAK) mc.options.sneakKey.setPressed(true);

        // 1) cos do wykopania w zasiegu? -> stoj i kop
        BlockPos target = findInReach(w, p);
        if (target != null) {
            mc.options.forwardKey.setPressed(false);
            minedThisLeg = true;
            idleTicks = 0;
            stuckTicks = 0;
            mine(mc, p, w, target);
            return;
        }

        if (mc.interactionManager.isBreakingBlock()) mc.interactionManager.cancelBlockBreaking();
        current = null;

        // 2) czy w ogole cos zostalo w terenie?
        if (tickCounter++ % 20 == 0 && !anyTargetLeft(w)) {
            stop(mc, "Gotowe! Nie ma wiecej blokow do wykopania.");
            return;
        }

        // trasa o dlugosci 0 -> stoimy w miejscu
        boolean noPath = pos1.getX() == pos2.getX() && pos1.getZ() == pos2.getZ();
        if (noPath) {
            mc.options.forwardKey.setPressed(false);
            if (++idleTicks > 100) stop(mc, "Zostaly bloki poza zasiegiem. Zatrzymano.");
            return;
        }

        // 3) idz lewo/prawo po trasie
        boolean alongX = Math.abs(pos1.getX() - pos2.getX()) >= Math.abs(pos1.getZ() - pos2.getZ());
        Vec3d end = Vec3d.ofCenter(headingToPos2 ? pos2 : pos1);
        double dx = end.x - p.getX();
        double dz = end.z - p.getZ();
        double remaining = alongX ? Math.abs(dx) : Math.abs(dz);

        if (remaining < 0.3) {
            // doszlismy do konca trasy -> zawroc
            mc.options.forwardKey.setPressed(false);
            idleLegs = minedThisLeg ? 0 : idleLegs + 1;
            minedThisLeg = false;
            if (idleLegs >= 2) {
                stop(mc, "Reszta blokow jest poza zasiegiem trasy. Zatrzymano.");
                return;
            }
            headingToPos2 = !headingToPos2;
            return;
        }

        float yaw = alongX ? (dx > 0 ? -90f : 90f) : (dz > 0 ? 0f : 180f);
        p.setYaw(yaw);
        p.setPitch(20f);
        mc.options.forwardKey.setPressed(true);

        if (p.getPos().squaredDistanceTo(lastPos) < 0.0004) {
            if (++stuckTicks > 60) {
                stop(mc, "Utknal - zatrzymano.");
                return;
            }
        } else {
            stuckTicks = 0;
        }
        lastPos = p.getPos();
    }

    // ---------------------------------------------------------------- kopanie

    private void mine(MinecraftClient mc, ClientPlayerEntity p, ClientWorld w, BlockPos target) {
        Direction face = exposedFace(w, target, p.getEyePos());
        if (face == null) return;

        if (!target.equals(current)) {
            mc.interactionManager.cancelBlockBreaking();
            current = target;
            selectBestTool(p, w.getBlockState(target));
        }

        lookAt(p, Vec3d.ofCenter(target).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5));

        if (mc.interactionManager.updateBlockBreakingProgress(target, face)) {
            p.swingHand(Hand.MAIN_HAND);
        }
    }

    /** Automatyczny wybor najlepszego narzedzia z paska skrotow. */
    private void selectBestTool(ClientPlayerEntity p, BlockState state) {
        PlayerInventory inv = p.getInventory();
        int best = inv.selectedSlot;
        float bestSpeed = speed(inv.getStack(best), state);
        for (int i = 0; i < 9; i++) {
            float sp = speed(inv.getStack(i), state);
            if (sp > bestSpeed + 0.01f) {
                bestSpeed = sp;
                best = i;
            }
        }
        inv.selectedSlot = best;
    }

    private float speed(ItemStack s, BlockState state) {
        if (s.isDamageable() && s.getMaxDamage() - s.getDamage() <= 2) return 0f; // nie niszcz narzedzia do konca
        return s.getMiningSpeedMultiplier(state);
    }

    private void lookAt(ClientPlayerEntity p, Vec3d to) {
        Vec3d d = to.subtract(p.getEyePos());
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        p.setYaw((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0));
        p.setPitch((float) -Math.toDegrees(Math.atan2(d.y, h)));
    }

    // ---------------------------------------------------------------- szukanie (teren kop1-kop2)

    private int[] bounds() {
        return new int[]{
                Math.min(kop1.getX(), kop2.getX()), Math.max(kop1.getX(), kop2.getX()),
                Math.min(kop1.getY(), kop2.getY()), Math.max(kop1.getY(), kop2.getY()),
                Math.min(kop1.getZ(), kop2.getZ()), Math.max(kop1.getZ(), kop2.getZ())
        };
    }

    private boolean isTarget(ClientWorld w, BlockPos pos) {
        BlockState s = w.getBlockState(pos);
        if (s.isAir() || !blocks.contains(s.getBlock())) return false;
        return s.getHardness(w, pos) >= 0; // pomin niezniszczalne
    }

    private BlockPos findInReach(ClientWorld w, ClientPlayerEntity p) {
        Vec3d eye = p.getEyePos();
        int r = (int) Math.ceil(REACH) + 1;
        int[] b = bounds();
        int x0 = Math.max(b[0], (int) Math.floor(eye.x) - r), x1 = Math.min(b[1], (int) Math.floor(eye.x) + r);
        int y0 = Math.max(b[2], (int) Math.floor(eye.y) - r), y1 = Math.min(b[3], (int) Math.floor(eye.y) + r);
        int z0 = Math.max(b[4], (int) Math.floor(eye.z) - r), z1 = Math.min(b[5], (int) Math.floor(eye.z) + r);

        BlockPos best = null;
        double bestD = REACH * REACH;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    m.set(x, y, z);
                    if (!isTarget(w, m)) continue;
                    double d = Vec3d.ofCenter(m).squaredDistanceTo(eye);
                    if (d > bestD) continue;
                    if (exposedFace(w, m, eye) == null) continue;
                    bestD = d;
                    best = m.toImmutable();
                }
        return best;
    }

    private boolean anyTargetLeft(ClientWorld w) {
        int[] b = bounds();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = b[0]; x <= b[1]; x++)
            for (int y = b[2]; y <= b[3]; y++)
                for (int z = b[4]; z <= b[5]; z++) {
                    m.set(x, y, z);
                    if (isTarget(w, m)) return true;
                }
        return false;
    }

    private Direction exposedFace(ClientWorld w, BlockPos pos, Vec3d eye) {
        Direction best = null;
        double bestD = Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            BlockState ns = w.getBlockState(n);
            if (ns.isSideSolidFullSquare(w, n, d.getOpposite())) continue;
            Vec3d c = Vec3d.ofCenter(pos).add(d.getOffsetX() * 0.5, d.getOffsetY() * 0.5, d.getOffsetZ() * 0.5);
            double dist = c.squaredDistanceTo(eye);
            if (dist < bestD) {
                bestD = dist;
                best = d;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- pomocnicze

    private void releaseKeys(MinecraftClient mc) {
        mc.options.forwardKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    private void msg(MinecraftClient mc, String text) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Sculc] " + text), false);
    }
}
