package com.meekdev.amnetic.client.model;

import java.util.function.Supplier;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

public class ModelBlockEntity extends BlockEntity {

    private final Supplier<Model> modelSupplier;
    private WorldModels.Placement placement;

    public ModelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Supplier<Model> modelSupplier) {
        super(type, pos, state);
        this.modelSupplier = modelSupplier;
    }

    protected void configurePlacement(WorldModels.Placement placement, BlockState state) {
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level.isClientSide()) {
            attach();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        detach();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level != null && level.isClientSide()) {
            attach();
        }
    }

    private void attach() {
        if (placement != null) {
            return;
        }
        Model model = modelSupplier.get();
        if (model == null) {
            return;
        }
        placement = WorldModels.place(model, getBlockPos());
        configurePlacement(placement, getBlockState());
    }

    private void detach() {
        if (placement != null) {
            placement.remove();
            placement = null;
        }
    }
}
