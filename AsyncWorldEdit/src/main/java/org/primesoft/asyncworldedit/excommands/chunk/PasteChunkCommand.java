/*
 * AsyncWorldEdit a performance improvement plugin for Minecraft WorldEdit plugin.
 * Copyright (c) 2015, SBPrime <https://github.com/SBPrime/>
 * Copyright (c) AsyncWorldEdit contributors
 *
 * All rights reserved.
 *
 * Redistribution in source, use in source and binary forms, with or without
 * modification, are permitted free of charge provided that the following 
 * conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 * 2.  Redistributions of source code, with or without modification, in any form
 *     other then free of charge is not allowed,
 * 3.  Redistributions of source code, with tools and/or scripts used to build the 
 *     software is not allowed,
 * 4.  Redistributions of source code, with information on how to compile the software
 *     is not allowed,
 * 5.  Providing information of any sort (excluding information from the software page)
 *     on how to compile the software is not allowed,
 * 6.  You are allowed to build the software for your personal use,
 * 7.  You are allowed to build the software using a non public build server,
 * 8.  Redistributions in binary form in not allowed.
 * 9.  The original author is allowed to redistrubute the software in bnary form.
 * 10. Any derived work based on or containing parts of this software must reproduce
 *     the above copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided with the
 *     derived work.
 * 11. The original author of the software is allowed to change the license
 *     terms or the entire license of the software as he sees fit.
 * 12. The original author of the software is allowed to sublicense the software
 *     or its parts using any license terms he sees fit.
 * 13. By contributing to this project you agree that your contribution falls under this
 *     license.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.primesoft.asyncworldedit.excommands.chunk;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.BlockVector2;
import org.primesoft.asyncworldedit.api.worldedit.IAweEditSession;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.World;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.primesoft.asyncworldedit.api.IWorld;
import org.primesoft.asyncworldedit.api.directChunk.IChangesetChunkData;
import org.primesoft.asyncworldedit.api.directChunk.ISerializedEntity;
import org.primesoft.asyncworldedit.api.directChunk.IWrappedChunk;
import org.primesoft.asyncworldedit.api.inner.IAsyncWorldEditCore;
import org.primesoft.asyncworldedit.api.playerManager.IPlayerEntry;
import org.primesoft.asyncworldedit.directChunk.ChangesetChunkExtent;
import org.primesoft.asyncworldedit.directChunk.CompoundTagUtils;
import org.primesoft.asyncworldedit.directChunk.DcUtils;
import org.primesoft.asyncworldedit.utils.InOutParam;
import org.primesoft.asyncworldedit.utils.Pair;
import org.primesoft.asyncworldedit.utils.PositionHelper;

/**
 *
 * @author SBPrime
 */
public class PasteChunkCommand extends DCMaskCommand {

    //private final static BaseBlock AIR = new BaseBlock(0);

    private final BlockVector3 m_location;
    private final World m_locationWorld;
    private final Transform m_transform;
    private final Clipboard m_clipboard;
    private final boolean m_skipAir;
    private final boolean m_updateLight;    
    private final boolean m_copyBiome;

    public PasteChunkCommand(ClipboardHolder clipboardHolder, Location location, World world,
            IAsyncWorldEditCore awe, boolean skipAir, boolean updateLight, boolean copyBiome,
            Mask destinationMask, IPlayerEntry playerEntry) {
        super(awe, destinationMask, playerEntry);

        m_location = BlockVector3.at(
                PositionHelper.positionToBlockPosition(location.getX()),
                (int) location.getY(),
                PositionHelper.positionToBlockPosition(location.getZ())
        );

        m_locationWorld = world;
        m_transform = clipboardHolder.getTransform();
        m_clipboard = clipboardHolder.getClipboard();

        m_skipAir = skipAir;
        m_updateLight = updateLight;
        m_copyBiome = copyBiome;
    }

    @Override
    public String getName() {
        return "chunkPaste";
    }

    @Override
    public Integer task(IAweEditSession editSesstion) throws WorldEditException {
        final IWorld world = m_weIntegrator.getWorld(m_locationWorld);
        final Transform reverse = m_transform.inverse();
        final Region region = m_clipboard.getRegion();
        final BlockVector3 from = m_clipboard.getOrigin();

        final InOutParam<BlockVector2> minIO = InOutParam.Out();
        final InOutParam<BlockVector2> maxIO = InOutParam.Out();

        findMinMax(from, region, minIO, maxIO);

        final BlockVector2 min = minIO.getValue();
        final BlockVector2 max = maxIO.getValue();

        int cMaxX = PositionHelper.positionToChunk(max.getX());
        int cMaxZ = PositionHelper.positionToChunk(max.getZ());

        int cMinX = PositionHelper.positionToChunk(min.getX());
        int cMinZ = PositionHelper.positionToChunk(min.getZ());

        int changedBlocks = 0;

        HashMap<BlockVector2, IWrappedChunk> dataCache = cacheChunks(cMinX, cMaxX, cMinZ, cMaxZ, world, editSesstion);
        HashMap<BlockVector2, List<Pair<Location, Entity>>> entityCache = aggregateEntities(from.toVector3());

        for (Map.Entry<BlockVector2, IWrappedChunk> entrySet : dataCache.entrySet()) {
            final BlockVector2 cPos = entrySet.getKey();
            final IWrappedChunk wChunk = entrySet.getValue();
            final IChangesetChunkData cData = m_chunkApi.createLazyChunkData(wChunk);
            final ChangesetChunkExtent extent = new ChangesetChunkExtent(cData);

            cData.setChunkCoords(cPos);

            maskSetExtent(extent);
            changedBlocks += setBlocks(cPos, reverse, from, region, cData);
            changedBlocks += addEntities(entityCache, cPos, cData);
            maskSetExtent(null);

            editSesstion.doCustomAction(new SetChangesetChunkChange(wChunk, cData), false);
            if (m_updateLight) {
                RelightChunkCommand.relightChunk(editSesstion, wChunk);
            }
        }

        return changedBlocks;
    }

    private int addEntities(HashMap<BlockVector2, List<Pair<Location, Entity>>> entityCache, BlockVector2 cPos,
            IChangesetChunkData cData) {
        if (!entityCache.containsKey(cPos)) {
            return 0;
        }

        final Vector3 chunkZero = PositionHelper.chunkToPosition(cPos, 0).toVector3();
        final List<Pair<Location, Entity>> entities = entityCache.get(cPos);
        final Map<UUID, Pair<ISerializedEntity, UUID>> serialised = new HashMap<>();

        for (Pair<Location, Entity> entry : entities) {
            Location pos = entry.getX1();
            Entity entity = entry.getX2();

            Vector3 target = pos.toVector();
            if (maskTest(target)) {
                ISerializedEntity serializedEntity = cData.addEntity(target.subtract(chunkZero), entity);

                if (serializedEntity != null) {
                    serializedEntity.setPitch(pos.getPitch());
                    serializedEntity.setYaw(pos.getYaw());

                    UUID sEntityId = CompoundTagUtils.getUUID(entity);
                    UUID sRiding = CompoundTagUtils.getRidingUUID(entity);

                    serialised.put(sEntityId, new Pair<>(serializedEntity, sRiding));
                }
            }
        }

        HashSet<UUID> toRemove = new HashSet<>();
        for (Pair<ISerializedEntity, UUID> entry : serialised.values()) {
            ISerializedEntity entity = entry.getX1();
            UUID ridingId = entry.getX2();

            if (ridingId != null && serialised.containsKey(ridingId)) {
                if (!toRemove.contains(ridingId)) {
                    toRemove.add(ridingId);
                }

                entity.setVehicle(serialised.get(ridingId).getX1());
            }
        }

        for (UUID uuid : toRemove) {
            ISerializedEntity entity = serialised.get(uuid).getX1();

            if (maskTest(chunkZero.add(entity.getPosition()))) {
                cData.removeEntity(entity);
            }
        }

        return serialised.size() - toRemove.size();
    }

    private int setBlocks(final BlockVector2 cPos, final Transform reverse,
            final BlockVector3 from, final Region region, IChangesetChunkData cData) {
        //TODO: 1.13
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.                
/*        final Vector3 chunkZero = PositionHelper.chunkToPosition(cPos, 0);
        int changedBlocks = 0;
        for (int x = 0; x < 16; x++) {
            final Vector3 xPos = chunkZero.add(x, 0, 0);
            for (int z = 0; z < 16; z++) {
                final Vector3 zPos = xPos.add(0, 0, z);
                for (int py = 0; py < 256; py++) {
                    final Vector3 yPos = zPos.add(0, py, 0);

                    final Vector3 transformed = reverse.apply(yPos.subtract(m_location)).add(from);
                    final Vector3 read = Vector3.at(Math.round(transformed.getX()), Math.round(transformed.getY()), Math.round(transformed.getZ()));

                    if (region.contains(read) && maskTest(yPos)) {
                        final BlockStateHolder bBlock = BlockTransformExtent.transform(m_clipboard.getBlock(read), m_transform);
                        final int data = bBlock.getData();
                        final int type = bBlock.getType();
                        final char id = m_chunkApi.getCombinedId(type, data);
                        final CompoundTag ct = bBlock.getNbtData();

                        boolean changed = false;
                        if (!bBlock.isAir() || !m_skipAir) {
                            if (ct != null) {
                                cData.setTileEntity(x, py, z, id, ct);
                            } else {
                                cData.setBlock(x, py, z, id);
                            }
                            changedBlocks++;

                            if (m_copyBiome) {
                                BaseBiome biome = m_clipboard.getBiome(read.toVector2D());
                                if (biome != null) {
                                    cData.setBiome(x, z, biome.getId());
                                }
                            }
                        }

                        if (changed) {
                            
                        }
                    }
                }
            }
        }

        return changedBlocks;*/
    }

    private HashMap<BlockVector2, IWrappedChunk> cacheChunks(int cMinX, int cMaxX, int cMinZ, int cMaxZ,
            final IWorld world, IAweEditSession editSesstion) throws WorldEditException {
        final HashMap<BlockVector2, IWrappedChunk> dataCatch = new HashMap<>();
        for (int cx = cMinX; cx <= cMaxX; cx++) {
            for (int cz = cMinZ; cz <= cMaxZ; cz++) {
                final BlockVector2 cPos = BlockVector2.at(cx, cz);

                final IWrappedChunk chunk = DcUtils.wrapChunk(m_taskDispatcher, m_chunkApi,
                        m_locationWorld, world, getPlayer(), cPos);

                dataCatch.put(cPos, chunk);
            }
        }

        return dataCatch;
    }

    /**
     * Aggregate all entites from the clipboard int chunk coords and calculate
     * the target coords
     *
     * @return
     */
    private HashMap<BlockVector2, List<Pair<Location, Entity>>> aggregateEntities(final Vector3 from) {
        HashMap<BlockVector2, List<Pair<Location, Entity>>> result = new HashMap<>();
        Vector3 location = m_location.toVector3();

        for (Entity e : m_clipboard.getEntities()) {
            if (e == null) {
                continue;
            }
            Location pos = e.getLocation();
            Vector3 direction = pos.getDirection();
            Vector3 v = pos.toVector();
            Vector3 targetPosition = m_transform.apply(v.subtract(from).subtract(0.5, 0.5, 0.5)).add(location).add(0.5, 0.5, 0.5);
            BlockVector2 cPos = PositionHelper.positionToChunk(targetPosition);

            List<Pair<Location, Entity>> list;
            if (result.containsKey(cPos)) {
                list = result.get(cPos);
            } else {
                list = new LinkedList<>();
                result.put(cPos, list);
            }

            Location newPos = new Location(m_clipboard, targetPosition,
                    m_transform.apply(direction).subtract(m_transform.apply(Vector3.ZERO)).normalize());
            list.add(new Pair<>(newPos, e));
        }

        return result;
    }

    /**
     * Find the minimum and maximum transformed block position
     *
     * @param from
     * @param region
     * @param min
     * @param max
     */
    private void findMinMax(BlockVector3 from, Region region,
            InOutParam<BlockVector2> min, InOutParam<BlockVector2> max) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;

        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockVector3 pos : region) {
            BlockVector3 transformedPos = m_transform.apply(pos.subtract(from).toVector3()).toBlockPoint().add(m_location);

            int x = PositionHelper.positionToBlockPosition(transformedPos.getX());
            int z = PositionHelper.positionToBlockPosition(transformedPos.getZ());

            if (x < minX) {
                minX = x;
            }
            if (z < minZ) {
                minZ = z;
            }

            if (x > maxX) {
                maxX = x;
            }
            if (z > maxZ) {
                maxZ = z;
            }
        }

        min.setValue(BlockVector2.at(minX, minZ));
        max.setValue(BlockVector2.at(maxX, maxZ));
    }
}
