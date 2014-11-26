/*
 * Copyright 2014 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.manualLabor.systems;

import org.terasology.asset.AssetType;
import org.terasology.asset.AssetUri;
import org.terasology.asset.Assets;
import org.terasology.durability.components.DurabilityComponent;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.manualLabor.components.IncreaseToolDurabilityComponent;
import org.terasology.manualLabor.components.MultiplyToolDurabilityComponent;
import org.terasology.manualLabor.events.ModifyToolCreationEvent;
import org.terasology.substanceMatters.components.MaterialCompositionComponent;
import org.terasology.substanceMatters.components.MaterialItemComponent;
import org.terasology.substanceMatters.components.SubstanceComponent;
import org.terasology.tintOverlay.TintOverlayIconComponent;

import java.util.Map;

@RegisterSystem
public class ModifyToolCreationSystem extends BaseComponentSystem {

    /**
     * Mimic any tint overlay items with an input item
     */
    @ReceiveEvent
    public void onToolHasTintOverlayIcon(ModifyToolCreationEvent event, EntityRef toolEntity, TintOverlayIconComponent tintOverlayIconComponent) {
        // check all the overlay items for a match to this input item's icon
        for (Map.Entry<String, TintOverlayIconComponent.TintParameter> overlayItem : tintOverlayIconComponent.texture.entrySet()) {
            AssetUri toolItemIcon = Assets.resolveAssetUri(AssetType.SUBTEXTURE, overlayItem.getKey());
            boolean matchedIcon = false;

            for (EntityRef inputItem : event.getInputItems()) {
                MaterialItemComponent inputItemMaterialItemComponent = inputItem.getComponent(MaterialItemComponent.class);
                MaterialCompositionComponent inputItemMaterialCompositionComponent = inputItem.getComponent(MaterialCompositionComponent.class);
                AssetUri inputItemIcon = Assets.resolveAssetUri(AssetType.SUBTEXTURE, inputItemMaterialItemComponent.icon);

                if (toolItemIcon.equals(inputItemIcon)) {
                    // change the appearance of this overlay (dont change the offset)
                    String substance = inputItemMaterialCompositionComponent.getPrimarySubstance();
                    TintOverlayIconComponent.TintParameter tintParameter = overlayItem.getValue();

                    setTintParametersFromSubstance(substance, tintParameter);

                    matchedIcon = true;
                }
            }

            // if we did not match up this overlay item to an item, and the hue was previously set, use the overall substance to tint
            if (!matchedIcon && overlayItem.getValue().hue != null) {
                setTintParametersFromSubstance(toolEntity.getComponent(MaterialCompositionComponent.class).getPrimarySubstance(), overlayItem.getValue());
            }
        }

        toolEntity.saveComponent(tintOverlayIconComponent);
    }

    private void setTintParametersFromSubstance(String substance, TintOverlayIconComponent.TintParameter tintParameter) {
        Prefab substancePrefab = Assets.getPrefab(substance);
        SubstanceComponent substanceComponent = substancePrefab.getComponent(SubstanceComponent.class);
        tintParameter.hue = substanceComponent.hue;
        tintParameter.brightnessScale = substanceComponent.brightnessScale;
        tintParameter.saturationScale = substanceComponent.saturationScale;
    }

    /**
     * Modify the original durability based on the substances used in its creation
     */
    @ReceiveEvent
    public void onModifyToolDurability(ModifyToolCreationEvent event, EntityRef toolEntity, DurabilityComponent durabilityComponent) {
        MaterialCompositionComponent materialCompositionComponent = toolEntity.getComponent(MaterialCompositionComponent.class);

        if (materialCompositionComponent != null) {
            // increase the tool's base durability
            for (Map.Entry<String, Float> substance : materialCompositionComponent.contents.entrySet()) {
                Prefab substancePrefab = Assets.getPrefab(substance.getKey());

                IncreaseToolDurabilityComponent substanceIncrease = substancePrefab.getComponent(IncreaseToolDurabilityComponent.class);
                if (substanceIncrease != null) {
                    durabilityComponent.maxDurability += substanceIncrease.increasePerSubstanceAmount * substance.getValue();
                }
            }

            // multiply the durability
            for (Map.Entry<String, Float> substance : materialCompositionComponent.contents.entrySet()) {
                Prefab substancePrefab = Assets.getPrefab(substance.getKey());

                MultiplyToolDurabilityComponent substanceMultiply = substancePrefab.getComponent(MultiplyToolDurabilityComponent.class);
                if (substanceMultiply != null) {
                    durabilityComponent.maxDurability *= Math.pow(substanceMultiply.multiplyPerSubstanceAmount, substance.getValue());
                }
            }

            // ensure the tool's initial durability is reset
            durabilityComponent.durability = durabilityComponent.maxDurability;

            toolEntity.saveComponent(durabilityComponent);
        }
    }
}