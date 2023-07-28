package com.cstav.evenmoreinstruments.block.blockentity;

import java.util.Optional;

import org.slf4j.Logger;

import com.cstav.evenmoreinstruments.Main;
import com.cstav.evenmoreinstruments.block.LooperBlock;
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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(bus = Bus.FORGE, modid = Main.MODID)
public class LooperBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    public CompoundTag getChannel() {
        return getChannel(getPersistentData());
    }
    public CompoundTag getChannel(final CompoundTag data) {
        return CommonUtil.getOrCreateElementTag(data, "channels");
    }


    public LooperBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.LOOPER.get(), pPos, pBlockState);

        final CompoundTag data = getPersistentData();

        // Construct all the data stuff
        getChannel();

        if (!data.contains("isRecording", CompoundTag.TAG_BYTE))
            setRecording(false);

        if (!data.contains("ticks", CompoundTag.TAG_INT))
            setTicks(0);
        if (!data.contains("repeatTick", CompoundTag.TAG_INT))
            setRepeatTick(-1);
    }
    

    public void setRecording(final boolean recording) {
        getPersistentData().putBoolean("recording", recording);
    }
    public void setTicks(final int ticks) {
        getPersistentData().putInt("ticks", ticks);
    }
    public void setRepeatTick(final int tick) {
        getPersistentData().putInt("repeatTick", tick);
    }

    public boolean isRecording() {
        return getPersistentData().getBoolean("recording");
    }
    public int getTicks() {
        return getPersistentData().getInt("ticks");
    }
    public int getRepeatTick() {
        return getPersistentData().getInt("repeatTick");
    }


    protected void addNote(NoteSound sound, ResourceLocation instrumentId, int pitch, int timestamp) {
        final CompoundTag channel = getChannel();
        final CompoundTag noteTag = new CompoundTag();


        noteTag.putInt("pitch", pitch);

        noteTag.putString("mono", sound.getMono().getLocation().toString());
        sound.getStereo().ifPresent((stereo) ->
            noteTag.putString("stereo", stereo.getLocation().toString())
        );

        noteTag.putInt("timestamp", timestamp);


        channel.getList("notes", CompoundTag.TAG_COMPOUND).add(noteTag);
    }


    public void tick(Level pLevel, BlockPos pPos, BlockState pState) {
        // idk why but it needs to be here for it to work
        final LooperBlockEntity lbe = getLBE(pLevel, pPos);

        
        if (!lbe.getBlockState().getValue(LooperBlock.PLAYING) && !lbe.isRecording())
            return;

        int ticks = getTicks() + 1;
        final int repTick = getRepeatTick();
        if ((repTick != -1) && (ticks > repTick))
            ticks = 0;


        final CompoundTag channel = getChannel();
        final String instrumentId = channel.getString("instrumentId");

        for (final Tag pNote : channel.getList("notes", Tag.TAG_COMPOUND)) {
            if (!(pNote instanceof CompoundTag))
                continue;

            final CompoundTag note = (CompoundTag)pNote;
            if (ticks != note.getInt("timestamp"))
                continue;

            try {
                final String stereoLoc = note.getString("stereo");
                
                ServerUtil.sendPlayNotePackets(pLevel, pPos,
                    new NoteSound(
                        SoundEvent.createVariableRangeEvent(new ResourceLocation(note.getString("mono"))),
                        stereoLoc.equals("") ? Optional.empty() : Optional.of(
                            SoundEvent.createVariableRangeEvent(new ResourceLocation(stereoLoc))
                        )
                    ), new ResourceLocation(instrumentId),
                    note.getInt("pitch")
                );

            } catch (Exception e) {
                LOGGER.error("Attempted to play note, but met with an exception", e);
            }
        }


        lbe.setTicks(ticks);
        lbe.setChanged();
    }


    /**
     * Attempts to get the looper pointed out by {@code instrument}. Removes its reference if not found.
     * @param level The level to get the BE from
     * @param instrument The subject instrument to get the looper data from
     * @return The Looper's block entity as pointed in the {@code instrument}'s data.
     * Null if not found
     */
    public static LooperBlockEntity getLBE(final Level level, final ItemStack instrument) {
        if (!LooperUtil.hasLooperTag(instrument))
            return null;

        final LooperBlockEntity looperBE = getLBE(level, LooperUtil.getLooperPos(instrument));

        if (looperBE == null)
            LooperUtil.remLooperTag(instrument);

        return looperBE;
    }
    public static LooperBlockEntity getLBE(final Level level, final BlockPos pos) {
        final Optional<LooperBlockEntity> opLooperBE =
            level.getBlockEntity(pos, ModBlockEntities.LOOPER.get());

        return opLooperBE.isPresent() ? opLooperBE.get() : null;
    }

    @SubscribeEvent
    public static void onInstrumentPlayed(final InstrumentPlayedEvent.ByPlayer event) {
        //TODO implement support for block instruments
        if (!LooperUtil.isRecording(event.instrument.get()))
            return;
            
            
        final LooperBlockEntity looperBE = getLBE(event.player.level(), event.instrument.get());
        if (looperBE == null)
            return;
            

        looperBE.setRecording(true);
            
        looperBE.addNote(event.sound, event.instrumentId, event.pitch, looperBE.getTicks());

        looperBE.setChanged();
    }
    
}
