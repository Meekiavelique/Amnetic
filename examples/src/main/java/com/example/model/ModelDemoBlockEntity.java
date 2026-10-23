package com.example.model;

import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ModelBlockEntity;
import com.meekdev.amnetic.client.model.Models;
import com.meekdev.amnetic.client.model.WorldModels;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

public class ModelDemoBlockEntity extends ModelBlockEntity {

    private static final Identifier MODEL = Identifier.fromNamespaceAndPath("example", "models3d/refrigerator.glb");

    public ModelDemoBlockEntity(BlockPos pos, BlockState state) {
        super(ModelDemo.BLOCK_ENTITY, pos, state, ModelDemoBlockEntity::loadModel);
    }

    private static Model loadModel() {
        return Models.load(MODEL);
    }

    @Override
    protected void configurePlacement(WorldModels.Placement placement, BlockState state) {
        placement.playFirst();
    }
}
