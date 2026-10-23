package com.example.model;

import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ModelBlockEntity;
import com.meekdev.amnetic.client.model.Models;
import com.meekdev.amnetic.client.model.WorldModels;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

public class B1BlockEntity extends ModelBlockEntity {

    private static final Identifier MODEL = Identifier.fromNamespaceAndPath("example", "models3d/b1_bis.glb");

    public B1BlockEntity(BlockPos pos, BlockState state) {
        super(B1Demo.BLOCK_ENTITY, pos, state, B1BlockEntity::loadModel);
    }

    private static Model loadModel() {
        return Models.load(MODEL);
    }

    @Override
    protected void configurePlacement(WorldModels.Placement placement, BlockState state) {
        placement.playFirst();
    }
}
