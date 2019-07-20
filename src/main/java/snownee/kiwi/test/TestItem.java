package snownee.kiwi.test;

import java.util.List;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import snownee.kiwi.client.RenderUtil;
//import snownee.kiwi.client.AdvancedFontRenderer;
import snownee.kiwi.item.ModItem;
import snownee.kiwi.util.MathUtil;

// Your class don't have to extends ModItem or ModBlock to be registered
public class TestItem extends ModItem
{
    public static List<BlockPos> posList = Lists.newLinkedList();
    public static Vec3d start;
    public static Vec3d end;

    public TestItem(Item.Properties builder)
    {
        super(builder);
        MinecraftForge.EVENT_BUS.register(this);
    }

    //    @Override
    //    public FontRenderer getFontRenderer(ItemStack stack)
    //    {
    //        return AdvancedFontRenderer.INSTANCE;
    //    }

    @Override
    public ActionResultType onItemUse(ItemUseContext context)
    {
        return ActionResultType.PASS;
        //        World world = context.getWorld();
        //        Hand hand = context.getHand();
        //        PlayerEntity player = context.getPlayer();
        //        BlockPos pos = context.getPos();
        //        Direction face = context.getFace();
        //        BlockState state = Blocks.CARVED_PUMPKIN.getDefaultState().with(CarvedPumpkinBlock.FACING, player.getHorizontalFacing().getOpposite());
        //        BlockPos result = PlayerUtil.tryPlace(world, pos, face, player, hand, state, null, true, true);
        //        return result == null ? ActionResultType.PASS : ActionResultType.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand handIn)
    {
        //        if (worldIn.isRemote)
        //        {
        //            Vec3d start = playerIn.getEyePosition(1).add(playerIn.getLookVec().scale(3));
        //            List<Vec3d> points = MathUtil.fibonacciSphere(start, 2, 100, true);
        //            for (Vec3d point : points)
        //            {
        //                worldIn.addParticle(ParticleTypes.FIREWORK, point.x, point.y, point.z, 0, 0, 0);
        //            }
        //        }

        ItemStack stack = playerIn.getHeldItem(handIn);
        //        NBTHelper data = NBTHelper.of(stack);
        //        RayTraceResult result = rayTrace(worldIn, playerIn, FluidMode.ANY);
        //        if (result != null && result.getType() == Type.BLOCK)
        //        {
        //            BlockPos pos = ((BlockRayTraceResult) result).getPos();
        //            data.setPos("pos", pos);
        //        }
        //        else
        //        {
        //            data.remove("pos");
        //        }
        posList.clear();
        start = playerIn.getEyePosition(1);
        end = start.add(playerIn.getLookVec().scale(15));
        MathUtil.posOnLine(start, end, posList);

        return new ActionResult<>(ActionResultType.SUCCESS, stack);
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void render(RenderWorldLastEvent event)
    {
        Screen.fill(0, 0, 20, 20, 20);
        Minecraft mc = Minecraft.getInstance();
        PlayerEntity player = mc.player;
        ItemStack stack = player.getHeldItemMainhand();
        if (stack.getItem() != TestModule.FIRST_ITEM)
        {
            return;
        }

        RenderUtil.beginWorld();
        GlStateManager.disableDepthTest();
        GlStateManager.disableTexture();
        GlStateManager.lineWidth(5);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(3, DefaultVertexFormats.POSITION_COLOR);
        if (start != null)
        {
            buffer.pos(start.x, start.y, start.z).color(0.5f, 0, 0, 0.5f).endVertex();
        }
        if (end != null)
        {
            buffer.pos(end.x, end.y, end.z).color(0.5f, 0, 0, 0.5f).endVertex();
        }
        for (BlockPos pos : posList)
        {
            AxisAlignedBB box = new AxisAlignedBB(pos);
            WorldRenderer.drawBoundingBox(buffer, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 0, 0, 0.5f, 0.5f);
        }
        tessellator.draw();
        RenderUtil.endWorld();

        GlStateManager.enableTexture();
        GlStateManager.enableDepthTest();
        //RenderUtil.drawPos(pos);
    }
}
