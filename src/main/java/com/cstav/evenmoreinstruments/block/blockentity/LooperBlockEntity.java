package com.cstav.evenmoreinstruments.block.blockentity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;

import com.cstav.evenmoreinstruments.Main;
import com.cstav.evenmoreinstruments.block.IDoubleBlock;
import com.cstav.evenmoreinstruments.block.LooperBlock;
import com.cstav.evenmoreinstruments.block.ModBlocks;
import com.cstav.evenmoreinstruments.gamerule.ModGameRules;
import com.cstav.evenmoreinstruments.util.CommonUtil;
import com.cstav.evenmoreinstruments.util.LooperUtil;
import com.cstav.genshinstrument.event.InstrumentPlayedEvent;
import com.cstav.genshinstrument.sound.NoteSound;
import com.cstav.genshinstrument.util.ServerUtil;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(bus = Bus.FORGE, modid = Main.MODID)
public class LooperBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final HashSet<LooperBlockEntity> RECORDING_LOOPERS = new HashSet<>();
    private UUID lockedBy;

    public CompoundTag getChannel() {
        return getChannel(getPersistentData());
    }
    public CompoundTag getChannel(final CompoundTag data) {
        return CommonUtil.getOrCreateElementTag(data, "channel");
    }

    public boolean hasChannel() {
        return getPersistentData().contains("channel");
    }


    public LooperBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.LOOPER.get(), pPos, pBlockState);

        final CompoundTag data = getPersistentData();

        // Construct all the data stuff
        if (!data.contains("ticks", CompoundTag.TAG_INT))
            setTicks(0);
        if (!data.contains("repeatTick", CompoundTag.TAG_INT))
            setRepeatTick(-1);
    }
    @Override
    public void setRemoved() {
        RECORDING_LOOPERS.remove(this);
    }
    

    public void setRecording(final boolean recording) {
        getPersistentData().putBoolean("recording", recording);

        if (recording)
            RECORDING_LOOPERS.add(this);
        else
            RECORDING_LOOPERS.remove(this);
    }

    public void setTicks(final int ticks) {
        getPersistentData().putInt("ticks", ticks);
    }
    /**
     * Increment the ticks of this looper by 1. Wrap back to the start
     * if the track finished playing.
     * @return The new tick value
     */
    public int incrementTick() {
        int ticks = getTicks();

        // Wrap back to the start when we finished playing
        final int repTick = getRepeatTick();
        if ((repTick != -1) && (ticks > repTick))
            ticks = 0;
        else
            ticks++;

        setTicks(ticks);
        setChanged();

        return ticks;
    }
    public void setRepeatTick(final int tick) {
        getPersistentData().putInt("repeatTick", tick);
    }

    public void setLockedBy(final UUID player) {
        lockedBy = player;
    }

    public void lock() {
        getPersistentData().putBoolean("locked", true);
        lockedBy = null;

        setRepeatTick(getTicks());
        setRecording(false);

        setChanged();
    }
    //TODO implement to Looper GUI
    /**
     * This method resets the looper, assuming it is not recording.
     */
    public void reset() {
        getPersistentData().remove("locked");
        getPersistentData().remove("lockedBy");
        lockedBy = null;

        setRepeatTick(-1);
        setTicks(0);

        getPersistentData().remove("channel");
    }

    public boolean isLocked() {
        return lockedByAnyone() || getPersistentData().getBoolean("locked");
    }
    public boolean isRecording() {
        return getPersistentData().getBoolean("recording");
    }

    public boolean isAllowedToRecord(final UUID player) {
        return !lockedByAnyone() || isLockedBy(player);
    }
    public boolean lockedByAnyone() {
        return lockedBy != null;
    }
    public boolean isLockedBy(final UUID player) {
        return lockedBy.equals(player);
    }

    public int getTicks() {
        return getPersistentData().getInt("ticks");
    }
    public int getRepeatTick() {
        return getPersistentData().getInt("repeatTick");
    }


    public void addNote(NoteSound sound, int pitch, float volume, int timestamp) {
        final CompoundTag channel = getChannel();
        final CompoundTag noteTag = new CompoundTag();


        noteTag.putInt("pitch", pitch);
        noteTag.putFloat("volume", volume);

        noteTag.putString("mono", sound.getMono().getLocation().toString());
        sound.getStereo().ifPresent((stereo) ->
            noteTag.putString("stereo", stereo.getLocation().toString())
        );

        noteTag.putInt("timestamp", timestamp);


        CommonUtil.getOrCreateListTag(channel, "notes").add(noteTag);
        setChanged();
    }


    public void tick(Level pLevel, BlockPos pPos, BlockState pState) {
        // idk why but it needs to be here for it to work
        final LooperBlockEntity lbe = getLBE(pLevel, pPos);
        final boolean isPlaying = lbe.getBlockState().getValue(LooperBlock.PLAYING);

        if (!isPlaying && !lbe.isRecording())
            return;

        final int ticks = lbe.incrementTick();

        if (!isPlaying)
            return;


        final CompoundTag channel = lbe.getChannel();
        final ResourceLocation instrumentId = new ResourceLocation(channel.getString("instrumentId"));

        for (final Tag pNote : channel.getList("notes", Tag.TAG_COMPOUND)) {
            if (!(pNote instanceof CompoundTag))
                continue;

            final CompoundTag note = (CompoundTag)pNote;
            if (ticks != note.getInt("timestamp"))
                continue;

            try {
                
                final String stereoLoc = note.getString("stereo");
                final int pitch = note.getInt("pitch");
                // Volume property did not exist before v2.1
                final float volume = note.contains("volume", Tag.TAG_FLOAT) ? note.getFloat("volume") : 1;
                
                ServerUtil.sendPlayNotePackets(pLevel, pPos,
                    new NoteSound(
                        SoundEvent.createVariableRangeEvent(new ResourceLocation(note.getString("mono"))),
                        stereoLoc.equals("") ? Optional.empty() : Optional.of(
                            SoundEvent.createVariableRangeEvent(new ResourceLocation(stereoLoc))
                        )
                    ), instrumentId,
                    pitch, volume
                );

                pLevel.blockEvent(pPos, ModBlocks.LOOPER.get(), 42, pitch);

            } catch (Exception e) {
                LOGGER.error("Attempted to play a looper note, but met with an exception", e);
            }
        }
    }



    public static LooperBlockEntity getLBE(final Level level, final ItemStack instrument) {
        return getLBE(level, LooperUtil.looperTag(instrument), () -> LooperUtil.remLooperTag(instrument));
    }
    public static LooperBlockEntity getLBE(final Level level, final BlockEntity instrument) {
        return getLBE(level, LooperUtil.looperTag(instrument), () -> {
            LooperUtil.remLooperTag(instrument);
            
            final BlockPos pos = instrument.getBlockPos();
            final BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof IDoubleBlock doubleBlock)
                LooperUtil.remLooperTag(level.getBlockEntity(doubleBlock.getOtherBlock(state, pos, level)));
        });
    }
    /**
     * Attempts to get the looper pointed out by {@code looperData}. Removes its reference if not found.
     * @return The Looper's block entity as pointed in the {@code instrument}'s data.
     * Null if not found
     */
    private static LooperBlockEntity getLBE(Level level, CompoundTag looperData, Runnable onInvalid) {
        if (looperData.isEmpty())
            return null;

        final LooperBlockEntity looperBE = getLBE(level, LooperUtil.getLooperPos(looperData));

        if (looperBE == null)
            onInvalid.run();

        return looperBE;
    }

    private static LooperBlockEntity getLBE(final Level level, final BlockPos pos) {
        return (level.getBlockEntity(pos) instanceof LooperBlockEntity lbe) ? lbe : null;
    }



    @SubscribeEvent
    public static void onInstrumentPlayed(final InstrumentPlayedEvent.ByPlayer event) {
        if (event.isClientSide || !LooperUtil.isRecording(LooperUtil.getLooperTagFromEvent(event)))
            return;

        final Level level = event.player.level();
            
        final LooperBlockEntity looperBE = event.itemInstrument.isPresent()
            ? getLBE(level, event.itemInstrument.get())
            : getLBE(level, event.level.getBlockEntity(event.blockInstrumentPos.get()));

        if (looperBE == null)
            return;

        if (looperBE.isCapped(level))
            return;


        if (looperBE.isLocked()) {
            if (!looperBE.isRecording() || !looperBE.isAllowedToRecord(event.player.getUUID()))
                return;
        } else {
            looperBE.setLockedBy(event.player.getUUID());
            looperBE.setRecording(true);
            looperBE.getChannel().putString("instrumentId", event.instrumentId.toString());
        }
            
        looperBE.addNote(event.sound, event.pitch, event.volume, looperBE.getTicks());
        looperBE.setChanged();
    }

    /**
     * A capped looper is a looper that cannot have any more notes in it, as defined in {@link ModGameRules#RULE_LOOPER_MAX_NOTES}.
     * Any negative will make the looper uncappable.
     * @return Whether this looper is capped
     */
    public boolean isCapped(final Level level) {
        final int cap = level.getGameRules().getInt(ModGameRules.RULE_LOOPER_MAX_NOTES);
        return (cap >= 0) && (getChannel().getList("notes", Tag.TAG_COMPOUND).size() >= cap);
    }


    // If the player leaves the world, we shouldn't record anymore
    @SubscribeEvent
    public static void onPlayerLeave(final PlayerLoggedOutEvent event) {
        final ArrayList<LooperBlockEntity> toBeRemoved = new ArrayList<>();

        //TODO keep track of where player recording to in (future) record data tag
        // Thus limit the possibilities of player recording at once to 1
        RECORDING_LOOPERS.forEach((looper) -> {
            if (looper.lockedBy.equals(event.getEntity().getUUID())) {
                looper.reset();
                looper.getPersistentData().putBoolean("recording", false);
                toBeRemoved.add(looper);
            }
        });

        RECORDING_LOOPERS.removeAll(toBeRemoved);
    }
    
}
