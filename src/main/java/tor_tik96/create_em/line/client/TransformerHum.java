package tor_tik96.create_em.line.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import tor_tik96.create_em.line.TransformerBlockEntity;
import tor_tik96.create_em.registry.EMSounds;

import java.util.HashMap;
import java.util.Map;

/** Петля гула для каждого работающего трансформатора (только клиент). */
public final class TransformerHum {
    private static final Map<BlockPos, Loop> PLAYING = new HashMap<>();

    public static void tick(TransformerBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        Loop loop = PLAYING.get(pos);
        if (be.getOutPerTick() > 0) {
            if (loop == null || loop.isStopped()) {
                loop = new Loop(be);
                PLAYING.put(pos.immutable(), loop);
                Minecraft.getInstance().getSoundManager().play(loop);
            } else {
                loop.fading = false; // нагрузка вернулась, пока гул затухал
            }
        } else if (loop != null) {
            loop.fadeOut();
        }
        PLAYING.values().removeIf(Loop::isStopped);
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final TransformerBlockEntity be;
        private boolean fading;

        Loop(TransformerBlockEntity be) {
            super(EMSounds.TRANSFORMER_HUM.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.be = be;
            this.looping = true;
            this.delay = 0;
            this.x = be.getBlockPos().getX() + .5;
            this.y = be.getBlockPos().getY() + .5;
            this.z = be.getBlockPos().getZ() + .5;
            this.volume = 0.01f;
            this.pitch = 1f;
            this.attenuation = Attenuation.LINEAR;
        }

        void fadeOut() {
            fading = true;
        }

        @Override
        public void tick() {
            if (be.isRemoved()) {
                stop();
                return;
            }
            // тихий гул на холостом ходу, громче под нагрузкой; плавно нарастает и затухает
            float target = fading ? 0f : 0.12f + 0.38f * be.getLoad();
            volume += (target - volume) * 0.1f;
            pitch = 0.95f + 0.1f * be.getLoad();
            if (fading && volume < 0.01f) stop();
        }
    }

    private TransformerHum() {}
}
