package igentuman.mbtool.common.item;

import ic2.api.item.IElectricItem;
import ic2.api.item.IElectricItemManager;
import ic2.api.item.ISpecialElectricItem;
import igentuman.mbtool.Mbtool;
import igentuman.mbtool.ModConfig;
import igentuman.mbtool.RegistryHandler;
import igentuman.mbtool.network.ModPacketHandler;
import igentuman.mbtool.network.NetworkMessage;
import igentuman.mbtool.recipe.MultiblockRecipe;
import igentuman.mbtool.recipe.MultiblockRecipes;
import mekanism.api.EnumColor;
import mekanism.api.energy.IEnergizedItem;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentUtils;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import org.lwjgl.opengl.GL11;

import java.util.List;

@Optional.InterfaceList(value = {
        @Optional.Interface(iface = "ic2.api.item.ISpecialElectricItem", modid = "ic2"),
        @Optional.Interface(iface = "ic2.api.item.IElectricItem", modid = "ic2"),
        @Optional.Interface(iface = "mekanism.api.energy.IEnergizedItem", modid = "mekanism")
})
public class ItemMultiBuilder extends Item implements ISpecialElectricItem, IElectricItem, IEnergizedItem {

    private static Object itemManagerIC2;

    public ItemMultiBuilder() {
        super();
        MinecraftForge.EVENT_BUS.register(this);
        setMaxDamage(ModConfig.general.mbtool_energy_capacity);
        this.setNoRepair();
        if(Mbtool.hooks.IC2Loaded) {
            itemManagerIC2 = new IC2ElectricManager();
        }
    }

    public CreativeTabs getCreativeTab()
    {
        return CreativeTabs.TOOLS;
    }

    public ItemMultiBuilder setItemName(String name)
    {
        setRegistryName(name);
        setTranslationKey(name);
        return this;
    }

    public EnumActionResult onItemUse(EntityPlayer player, World worldIn, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ)
    {
        super.onItemUse(player, worldIn, pos, hand, facing, hitX, hitY, hitZ);
         return EnumActionResult.FAIL;
    }

    int xd = 0;
    int zd = 0;

    public static String getEnergyDisplayRF(float energyVal)
    {
        String val = String.valueOf(MathHelper.floor(energyVal));

        return val + " RF";
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World worldIn, List<String> tooltip, ITooltipFlag flagIn)
    {
        tooltip.add(EnumColor.AQUA + "\u00a7o" + I18n.format("tooltip.mbtool.gui_key"));
        tooltip.add(EnumColor.AQUA + "\u00a7o" + I18n.format("tooltip.mbtool.rotate_keys"));

        String color = "";
        float rf = this.getElectricityStored(stack);

        if (rf <= this.getMaxElectricityStored(stack) / 3)
        {
            color = "\u00a74";
        } else if (rf > this.getMaxElectricityStored(stack) * 2 / 3)
        {
            color = "\u00a72";
        } else
        {
            color = "\u00a76";
        }

        tooltip.add(color + getEnergyDisplayRF(rf) + "/" + getEnergyDisplayRF(this.getMaxElectricityStored(stack)));

    }
    /**
     * Makes sure the item is uncharged when it is crafted and not charged.
     * Change this if you do not want this to happen!
     */
    @Override
    public void onCreated(ItemStack itemStack, World par2World, EntityPlayer par3EntityPlayer)
    {
        this.setElectricity(itemStack, 0);
    }

    public float recharge(ItemStack itemStack, float energy, boolean doReceive)
    {
        float rejectedElectricity = Math.max(this.getElectricityStored(itemStack) + energy - this.getMaxElectricityStored(itemStack), 0);
        float energyToReceive = energy - rejectedElectricity;
        if (energyToReceive > ModConfig.general.mbtool_energy_capacity/10)
        {
            rejectedElectricity += energyToReceive - ModConfig.general.mbtool_energy_capacity/10;
            energyToReceive  =ModConfig.general.mbtool_energy_capacity/10;
        }

        if (doReceive)
        {
            this.setElectricity(itemStack, this.getElectricityStored(itemStack) + energyToReceive);
        }

        return energyToReceive;
    }

    public float discharge(ItemStack itemStack, float energy, boolean doTransfer)
    {
        float thisEnergy = this.getElectricityStored(itemStack);
        float energyToTransfer = Math.min(Math.min(thisEnergy, energy), ModConfig.general.mbtool_energy_capacity/10);

        if (doTransfer)
        {
            this.setElectricity(itemStack, thisEnergy - energyToTransfer);
        }

        return energyToTransfer;
    }

    public int getTierGC(ItemStack itemStack)
    {
        return 1;
    }

    public void setElectricity(ItemStack itemStack, float rf)
    {
        if (itemStack.getTagCompound() == null)
        {
            itemStack.setTagCompound(new NBTTagCompound());
        }

        float electricityStored = Math.max(Math.min(rf, this.getMaxElectricityStored(itemStack)), 0);
        if (rf > 0F || itemStack.getTagCompound().hasKey("electricity"))
        {
            itemStack.getTagCompound().setFloat("electricity", electricityStored);
        }

        itemStack.setItemDamage(ModConfig.general.mbtool_energy_capacity - (int) (electricityStored / this.getMaxElectricityStored(itemStack) * ModConfig.general.mbtool_energy_capacity));
    }


    public int receiveEnergy(ItemStack container, int maxReceive, boolean simulate)
    {
        return (int) (this.recharge(container, ModConfig.general.mbtool_energy_capacity/10, !simulate));
    }

    public int extractEnergy(ItemStack container, int maxExtract, boolean simulate)
    {
        return (int) (this.discharge(container, ModConfig.general.mbtool_energy_capacity/10, !simulate));
    }

    public int getEnergyStored(ItemStack container)
    {
        return (int) (this.getElectricityStored(container));
    }

    public int getMaxEnergyStored(ItemStack container)
    {
        return (int) (this.getMaxElectricityStored(container));
    }

    // The following seven methods are for Mekanism compatibility

    @Optional.Method(modid = "mekanism")
    public double getEnergy(ItemStack itemStack)
    {
        return this.getElectricityStored(itemStack) * 0.1;
    }

    @Optional.Method(modid = "mekanism")
    public void setEnergy(ItemStack itemStack, double amount)
    {
        this.setElectricity(itemStack, (float) ((float) amount *  0.1));
    }

    @Optional.Method(modid = "mekanism")
    public double getMaxEnergy(ItemStack itemStack)
    {
        return this.getMaxElectricityStored(itemStack)  * 0.1;
    }

    @Optional.Method(modid = "mekanism")
    public double getMaxTransfer(ItemStack itemStack)
    {
        return ModConfig.general.mbtool_energy_capacity * 0.01;
    }

    @Optional.Method(modid = "mekanism")
    public boolean canReceive(ItemStack itemStack)
    {
        return true;
    }

    public boolean canSend(ItemStack itemStack)
    {
        return false;
    }

    public float getElectricityStored(ItemStack itemStack)
    {
        if (itemStack.getTagCompound() == null)
        {
            itemStack.setTagCompound(new NBTTagCompound());
        }
        float energyStored = 0f;
        if (itemStack.getTagCompound().hasKey("electricity"))
        {
            NBTBase obj = itemStack.getTagCompound().getTag("electricity");
            if (obj instanceof NBTTagDouble)
            {
                energyStored = ((NBTTagDouble) obj).getFloat();
            } else if (obj instanceof NBTTagFloat)
            {
                energyStored = ((NBTTagFloat) obj).getFloat();
            }
        } else
        {
            if (itemStack.getItemDamage() == ModConfig.general.mbtool_energy_capacity)
                return 0F;

            energyStored = this.getMaxElectricityStored(itemStack) * (ModConfig.general.mbtool_energy_capacity - itemStack.getItemDamage()) / ModConfig.general.mbtool_energy_capacity;
            itemStack.getTagCompound().setFloat("electricity", energyStored);
        }

        /** Sets the damage as a percentage to render the bar properly. */
        itemStack.setItemDamage(ModConfig.general.mbtool_energy_capacity - (int) (energyStored / this.getMaxElectricityStored(itemStack) * ModConfig.general.mbtool_energy_capacity));
        return energyStored;
    }

    public int getMaxElectricityStored(ItemStack item)
    {
        return ModConfig.general.mbtool_energy_capacity;
    }

    @Optional.Method(modid = "ic2")
    public IElectricItemManager getManager(ItemStack itemstack)
    {
        return (IElectricItemManager) itemManagerIC2;
    }

    @Optional.Method(modid = "ic2")
    public boolean canProvideEnergy(ItemStack itemStack)
    {
        return false;
    }

    @Optional.Method(modid = "ic2")
    public int getTier(ItemStack itemStack)
    {
        return 2;
    }

    @Optional.Method(modid = "ic2")
    public double getMaxCharge(ItemStack itemStack)
    {
        return this.getMaxElectricityStored(itemStack) / 4;
    }

    @Optional.Method(modid = "ic2")
    public double getTransferLimit(ItemStack itemStack)
    {
        return 0;
    }

    private boolean hasRecipe(ItemStack item)
    {
        try {
           return item.getTagCompound().hasKey("recipe");
        } catch (NullPointerException ignored) {
            return false;
        }
    }

    public BlockPos getRayTraceHit()
    {
        Minecraft mc = Minecraft.getMinecraft();

        Vec3d vec = mc.player.getLookVec();
        RayTraceResult rt = mc.player.rayTrace(10, 1f);
        if(!rt.typeOfHit.equals(RayTraceResult.Type.BLOCK)) {
            return null;
        }
        ItemStack mainItem = mc.player.getHeldItemMainhand();
        ItemStack secondItem = mc.player.getHeldItemOffhand();

        boolean main = !mainItem.isEmpty() && mainItem.getItem() == RegistryHandler.MBTOOL && hasRecipe(mainItem);
        boolean off = !secondItem.isEmpty() && secondItem.getItem() == RegistryHandler.MBTOOL && hasRecipe(secondItem);

        BlockPos hit = rt.getBlockPos();
        EnumFacing look = (Math.abs(vec.z) > Math.abs(vec.x)) ? (vec.z > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH) : (vec.x > 0 ? EnumFacing.EAST : EnumFacing.WEST);
        if(!hasRecipe(mainItem) && !hasRecipe(secondItem)) return null;
        IBlockState state = mc.player.world.getBlockState(hit);
        if (!state.getBlock().isReplaceable(mc.player.world, hit))
        {
            hit = hit.add(0, 1, 0);
        }
        MultiblockRecipe recipe;
        if(main) {
            recipe = MultiblockRecipes.getAvaliableRecipes().get(mainItem.getTagCompound().getInteger("recipe"));
        } else {
            recipe = MultiblockRecipes.getAvaliableRecipes().get(secondItem.getTagCompound().getInteger("recipe"));
        }
        int rotation = getRotation();
        hit = hit.add(-recipe.getWidth()/2, 0, -recipe.getDepth()/2+1);

        if(recipe.getWidth() % 2 != 0) {
            // hit = hit.add(-1, 0, 0);
        }

        if(recipe.getDepth() % 2 != 0) {
            //hit = hit.add(0, 0, -1);
        }
        return hit;
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void renderLast(RenderWorldLastEvent event)
    {
        Minecraft mc = Minecraft.getMinecraft();

        ItemStack mainItem = mc.player.getHeldItemMainhand();
        ItemStack secondItem = mc.player.getHeldItemOffhand();

        boolean main = !mainItem.isEmpty() && mainItem.getItem() == RegistryHandler.MBTOOL && hasRecipe(mainItem);
        boolean off = !secondItem.isEmpty() && secondItem.getItem() == RegistryHandler.MBTOOL && hasRecipe(secondItem);

        if(!main && !off) {
            return;
        }



        BlockPos hit = getRayTraceHit();
        if(hit == null) return;
        MultiblockRecipe recipe;
        if(main) {
            recipe = MultiblockRecipes.getAvaliableRecipes().get(mainItem.getTagCompound().getInteger("recipe"));
        } else {
            recipe = MultiblockRecipes.getAvaliableRecipes().get(secondItem.getTagCompound().getInteger("recipe"));
        }
        GlStateManager.pushMatrix();
        renderSchematic(mc.player, hit, event.getPartialTicks(), recipe);
        GlStateManager.popMatrix();
    }

    public int getRotation()
    {
        Minecraft mc = Minecraft.getMinecraft();

        ItemStack mainItem = mc.player.getHeldItemMainhand();
        ItemStack secondItem = mc.player.getHeldItemOffhand();

        boolean main = !mainItem.isEmpty() && mainItem.getItem() == RegistryHandler.MBTOOL && hasRecipe(mainItem);
        boolean off = !secondItem.isEmpty() && secondItem.getItem() == RegistryHandler.MBTOOL && hasRecipe(secondItem);
        ItemStack item = ItemStack.EMPTY;
        if(main) {
            item = mainItem;
        }
        if(off) {
            item = secondItem;
        }
        if(item.equals(ItemStack.EMPTY)) {
            return 0;
        }
        int rotation = 0;
        try {
            rotation = item.getTagCompound().getInteger("rotation");
        } catch (NullPointerException ignored) {  }
        return rotation;
    }

    int keyPressDelay = 10;

    public void setRotation(int dir, ItemStack item)
    {
        int rot = getRotation();
        rot+=dir;
        if(dir < 0 && rot < 0) {
            rot = 3;
        }
        if(dir > 0 && rot > 3) {
            rot = 0;
        }
        item.getTagCompound().setInteger("rotation", rot);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void handleKeypress(TickEvent.ClientTickEvent event)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if(mc.player == null) return;

        ItemStack mainItem = mc.player.getHeldItemMainhand();
        ItemStack secondItem = mc.player.getHeldItemOffhand();

        boolean main = !mainItem.isEmpty() && mainItem.getItem() == RegistryHandler.MBTOOL && hasRecipe(mainItem);
        boolean off = !secondItem.isEmpty() && secondItem.getItem() == RegistryHandler.MBTOOL && hasRecipe(secondItem);
        keyPressDelay--;
        if((!main && !off) || keyPressDelay > 0) {
            return;
        }

        if(Keyboard.isKeyDown(Keyboard.KEY_LEFT)) {
            if(main) setRotation(-1, mainItem);
            if(off) setRotation(-1, secondItem);
            keyPressDelay=10;
        }

        if(Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) {
            if(main) setRotation(1, mainItem);
            if(off) setRotation(1, secondItem);
            keyPressDelay=10;
        }
    }

    public void renderSchematic(EntityPlayer player, BlockPos hit, float partialTicks, MultiblockRecipe recipe)
    {
        Minecraft mc = Minecraft.getMinecraft();

        if(recipe == null) return;

        int mh = recipe.getHeight();
        int ml = recipe.getDepth();
        int mw = recipe.getWidth();

        double px = TileEntityRendererDispatcher.staticPlayerX;
        double py = TileEntityRendererDispatcher.staticPlayerY;
        double pz = TileEntityRendererDispatcher.staticPlayerZ;
        GlStateManager.translate(hit.getX() - px, hit.getY() - py, hit.getZ() - pz);

        GlStateManager.disableLighting();
        if (Minecraft.isAmbientOcclusionEnabled())
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
        else
            GlStateManager.shadeModel(GL11.GL_FLAT);

        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        BlockRendererDispatcher blockRender = mc.getBlockRendererDispatcher();

        int idx = 0;
        for (int h = 0; h < mh; h++) {
            for (int l = 0; l < ml; l++) {
                for (int w = 0; w < mw; w++) {
                    GlStateManager.pushMatrix();
                    BlockPos pos = new BlockPos(l, h, w);
                    if(!recipe.getStateAtBlockPos(pos).equals(Blocks.AIR.getDefaultState())) {
                        int xo = l;
                        int zo = w;
                        int rotation = getRotation();
                        switch (rotation)
                        {
                            case 1:
                                zo = l;
                                xo = (mw - w - 1);
                                break;
                            case 2:
                                xo = (ml - l - 1);
                                zo = (mw - w - 1);
                                break;
                            case 3:
                                zo = (ml - l - 1);
                                xo = w;
                                break;
                        }



                        IBlockState state = recipe.getStateAtBlockPos(pos);
                        NBTTagCompound tag = recipe.getVariantAtBlockPos(pos);
                        ItemStack stack = new ItemStack(state.getBlock());
                        BlockPos actualPos = hit.add(xo, h, zo);
                        IBlockState actualState = player.world.getBlockState(actualPos);
                        if(tag != null) {
                            IBlockState st = NBTUtil.readBlockState(tag);
                            st.getPropertyKeys().forEach( (iProperty) -> {

                             //   state.(iProperty, st.getValue(iProperty));
                            });
                        }
                        boolean isEmpty = player.world.getBlockState(actualPos).getBlock().isReplaceable(player.world, actualPos);
                        if(isEmpty) {
                            GlStateManager.translate(xo, h, zo+1);
                            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                            blockRender.renderBlockBrightness(state, 0.2f);
                        }

                    }
                    GlStateManager.popMatrix();

                    idx++;
                }
            }
        }
        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();


        idx = 0;
        GlStateManager.disableDepth();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        for (int h = 0; h < mh; h++) {
            for (int l = 0; l < ml; l++) {
                for (int w = 0; w < mw; w++) {
                    BlockPos pos = new BlockPos(l, h, w);
                    GlStateManager.pushMatrix();

                    int xo = l;
                    int zo = w;
                    int rotation = getRotation();
                    switch (rotation)
                    {
                        case 1:
                            zo = l;
                            xo = (mw - w - 1);
                            break;
                        case 2:
                            xo = (ml - l - 1);
                            zo = (mw - w - 1);
                            break;
                        case 3:
                            zo = (ml - l - 1);
                            xo = w;
                            break;
                    }
                    BlockPos actualPos = hit.add(xo, h, zo);

                    IBlockState otherState = null;
                    IBlockState state = recipe.getStateAtBlockPos(pos);
                    ItemStack stack = new ItemStack(state.getBlock());
                    IBlockState actualState = player.world.getBlockState(actualPos);
                    boolean stateEqual = actualState.equals(state);
                    boolean otherStateEqual = otherState == null ? false : otherState.equals(state);

                    boolean isEmpty = player.world.getBlockState(actualPos).getBlock().isReplaceable(player.world, actualPos);
                    if(!isEmpty || ((w == 0 || w == mw-1) && (l == 0 || l == ml-1) && (h == 0 || h == mh-1))) {

                        GlStateManager.pushMatrix();
                        GlStateManager.disableTexture2D();
                        GlStateManager.enableBlend();
                        GlStateManager.disableCull();
                        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                        float r = isEmpty ? 0 : 1;
                        float g = isEmpty ? 1 : 0;
                        float b = 0;
                        float alpha = .175F;


                        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
                        GlStateManager.glLineWidth(5f);
                        GlStateManager.translate(xo + .5, h + .5, zo + .5);
                        GlStateManager.scale(1.01, 1.01, 1.01);
                        if(!isEmpty || h == mh-1) { //top face
                            if (!isEmpty || w == 0) {
                                buffer.pos(-.5F, .5F, -.5F).color(r, g, b, alpha).endVertex();
                                buffer.pos(.5F, .5F, -.5F).color(r, g, b, alpha).endVertex();
                            }
                            if (!isEmpty || l == ml - 1) {
                                buffer.pos(.5F, .5F, -.5F).color(r, g, b, alpha).endVertex();
                                buffer.pos(.5F, .5F, .5F).color(r, g, b, alpha).endVertex();
                            }
                            if (!isEmpty || w == mw - 1) {
                                buffer.pos(.5F, .5F, .5F).color(r, g, b, alpha).endVertex();
                                buffer.pos(-.5F, .5F, .5F).color(r, g, b, alpha).endVertex();
                            }
                            if(!isEmpty || l == 0) {
                                buffer.pos(-.5F, .5F, .5F).color(r, g, b, alpha).endVertex();
                                buffer.pos(-.5F, .5F, -.5F).color(r, g, b, alpha).endVertex();
                            }

                        }
                        if(!isEmpty || (w == 0 && l == 0)) {
                            buffer.pos(-.5F, .5F, -.5F).color(r, g, b, alpha).endVertex();
                            buffer.pos(-.5F, -.5F, -.5F).color(r, g, b, alpha).endVertex();
                        }
                        if(!isEmpty ||(l == ml-1 && w == 0)) {
                            buffer.pos(.5F, .5F, -.5F).color(r, g, b, alpha).endVertex();
                            buffer.pos(.5F, -.5F, -.5F).color(r, g, b, alpha).endVertex();
                        }
                        if(!isEmpty ||(l == 0 && w == mw-1)) {
                            buffer.pos(-.5F, .5F, .5F).color(r, g, b, alpha).endVertex();
                            buffer.pos(-.5F, -.5F, .5F).color(r, g, b, alpha).endVertex();
                        }
                        if(!isEmpty || (w == mw-1 && l == ml-1)) {
                            buffer.pos(.5F, .5F, .5F).color(r, g, b, alpha).endVertex();
                            buffer.pos(.5F, -.5F, .5F).color(r, g, b, alpha).endVertex();
                        }

                        if(!isEmpty || h == 0) {
                            if (!isEmpty || w == 0) {
                                buffer.pos(-.5F, -.5F, -.5F).color(r, g, b, alpha).endVertex();
                                buffer.pos(.5F, -.5F, -.5F).color(r, g, b, alpha).endVertex();
                            }
                            if (!isEmpty || l == ml - 1) {
                                buffer.pos(.5F, -.5F, -.5F).color(r, g, b, alpha).endVertex();
                                buffer.pos(.5F, -.5F, .5F).color(r, g, b, alpha).endVertex();
                            }
                            if (!isEmpty || w == mw - 1) {
                                buffer.pos(.5F, -.5F, .5F).color(r, g, b, alpha).endVertex();
                                buffer.pos(-.5F, -.5F, .5F).color(r, g, b, alpha).endVertex();
                            }
                            if(!isEmpty || l == 0) {
                                buffer.pos(-.5F, -.5F, .5F).color(r, g, b, alpha).endVertex();
                                buffer.pos(-.5F, -.5F, -.5F).color(r, g, b, alpha).endVertex();
                            }
                        }
                        tessellator.draw();
                        GlStateManager.enableCull();
                        GlStateManager.disableBlend();
                        GlStateManager.enableTexture2D();
                        GlStateManager.popMatrix();
                    }

                    GlStateManager.popMatrix();
                    idx++;
                }
            }
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.enableBlend();

        GlStateManager.enableDepth();
    }

    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn)
    {
        if(playerIn.isSneaking()) {
            playerIn.openGui(Mbtool.instance, 0, worldIn, playerIn.getPosition().getX(), playerIn.getPosition().getY(), playerIn.getPosition().getZ());
            return ActionResult.newResult(EnumActionResult.SUCCESS, playerIn.getHeldItem(handIn));
        }
        if(keyPressDelay > 0) return ActionResult.newResult(EnumActionResult.FAIL, playerIn.getHeldItem(handIn));
        BlockPos hit = getRayTraceHit();
        if(hit == null) return super.onItemRightClick(worldIn, playerIn, handIn);
        Minecraft mc = Minecraft.getMinecraft();

        ItemStack mainItem = mc.player.getHeldItemMainhand();
        ItemStack secondItem = mc.player.getHeldItemOffhand();

        boolean main = !mainItem.isEmpty() && mainItem.getItem() == RegistryHandler.MBTOOL && hasRecipe(mainItem);
        boolean off = !secondItem.isEmpty() && secondItem.getItem() == RegistryHandler.MBTOOL && hasRecipe(secondItem);
        int recipeid;
        if(main) {
            recipeid = mainItem.getTagCompound().getInteger("recipe");
        } else {
            recipeid = secondItem.getTagCompound().getInteger("recipe");
        }
        keyPressDelay = 10;
        ModPacketHandler.instance.sendToServer(new NetworkMessage(hit, getRotation(), recipeid, playerIn.getUniqueID().toString()));

        return super.onItemRightClick(worldIn, playerIn, handIn);
    }


}
