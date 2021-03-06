package snownee.kiwi.crafting;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import net.minecraft.block.BlockState;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipe;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistryEntry;
import snownee.kiwi.KiwiManager;
import snownee.kiwi.util.NBTHelper;
import snownee.kiwi.util.Util;

public class TextureBlockRecipe extends DynamicShapedRecipe
{
    private final List<String> textureKeys;
    private final List<String> marks;
    private int keyCount = -1;

    public TextureBlockRecipe(ResourceLocation idIn, String groupIn, int recipeWidthIn, int recipeHeightIn, NonNullList<Ingredient> ingredients, ItemStack recipeOutputIn, List<String> textureKeys, List<String> marks)
    {
        super(idIn, groupIn, recipeWidthIn, recipeHeightIn, ingredients, recipeOutputIn);
        this.textureKeys = textureKeys;
        this.marks = marks;
    }

    @Override
    protected boolean checkMatch(CraftingInventory inv, int startX, int startY)
    {
        return checkMatchInternal(inv, startX, startY) != null;
    }

    private Map<String, ItemStack> checkMatchInternal(CraftingInventory inv, int startX, int startY)
    {
        Map<String, ItemStack> result = null;
        int i = 0;
        for (int y = startY; y < startY + getRecipeHeight(); ++y)
        {
            for (int x = startX; x < startX + getRecipeWidth(); ++x)
            {
                String key = textureKeys.get(i);
                if (key != null)
                {
                    if (result == null)
                    {
                        if (keyCount < 0)
                        {
                            Set<String> set = Sets.newHashSet(textureKeys);
                            set.remove(null);
                            keyCount = set.size();
                        }
                        result = Maps.newHashMapWithExpectedSize(keyCount);
                    }
                    ItemStack slotStack = inv.getStackInSlot(x + y * inv.getWidth());
                    Ingredient ingredient = getIngredients().get(x - startX + (y - startY) * getRecipeWidth());
                    if (!(ingredient instanceof FullBlockIngredient) && !FullBlockIngredient.isTextureBlock(slotStack))
                    {
                        return null;
                    }
                    ItemStack stack = result.getOrDefault(key, ItemStack.EMPTY);
                    if (stack.isEmpty())
                    {
                        result.put(key, slotStack);
                    }
                    else
                    {
                        if (!stack.isItemEqual(slotStack))
                        {
                            return null;
                        }
                    }
                }
                if (!matches(inv, x, y, x - startX, y - startY))
                {
                    return null;
                }
                ++i;
            }
        }
        return result;
    }

    @Override
    public ItemStack getCraftingResult(CraftingInventory inv)
    {
        int[] pos = getMatchPos(inv);
        if (pos == null)
        {
            return ItemStack.EMPTY;
        }
        Map<String, ItemStack> result = checkMatchInternal(inv, pos[0], pos[1]);
        if (result == null)
        {
            return ItemStack.EMPTY;
        }
        ItemStack stack = getRecipeOutput().copy();
        NBTHelper data = NBTHelper.of(stack.getOrCreateChildTag("BlockEntityTag"));
        for (Entry<String, ItemStack> e : result.entrySet())
        {
            Item item = e.getValue().getItem();
            if (item instanceof BlockItem)
            {
                BlockState state = ((BlockItem) item).getBlock().getDefaultState();
                for (String k : e.getKey().split(","))
                {
                    data.setString("Textures." + k, NBTUtil.writeBlockState(state).toString());
                    if (marks.contains(k))
                    {
                        data.setString("Items." + k, Util.trimRL(item.getRegistryName()));
                    }
                }
            }
            else
            {
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    @Override
    public IRecipeSerializer<?> getSerializer()
    {
        return KiwiManager.textureBlockSerializer;
    }

    public static class Serializer extends ForgeRegistryEntry<IRecipeSerializer<?>> implements IRecipeSerializer<TextureBlockRecipe>
    {
        @Override
        public TextureBlockRecipe read(ResourceLocation recipeId, JsonObject json)
        {
            String group = JSONUtils.getString(json, "group", "");
            Map<String, Ingredient> ingredientMap = ShapedRecipe.deserializeKey(JSONUtils.getJsonObject(json, "key"));
            String[] pattern = ShapedRecipe.shrink(ShapedRecipe.patternFromJson(JSONUtils.getJsonArray(json, "pattern")));
            int width = pattern[0].length();
            int height = pattern.length;
            NonNullList<Ingredient> nonnulllist = ShapedRecipe.deserializeIngredients(pattern, ingredientMap, width, height);
            ItemStack itemstack = ShapedRecipe.deserializeItem(JSONUtils.getJsonObject(json, "result"));

            Map<String, String> texMap = Maps.newHashMap();
            for (Entry<String, JsonElement> entry : JSONUtils.getJsonObject(json, "texture").entrySet())
            {
                if (entry.getKey().length() != 1)
                {
                    throw new JsonSyntaxException("Invalid key entry: '" + entry.getKey() + "' is an invalid symbol (must be 1 character only).");
                }
                if (" ".equals(entry.getKey()) || ",".equals(entry.getKey()))
                {
                    throw new JsonSyntaxException("Invalid key entry: '" + entry.getKey() + "' is a reserved symbol.");
                }
                if (entry.getValue().isJsonArray())
                {
                    /* off */
                    List<String> keys = Lists.newArrayList(entry.getValue().getAsJsonArray())
                            .stream()
                            .map(JsonElement::getAsString)
                            .collect(Collectors.toList());
                    /* on */
                    texMap.put(entry.getKey(), StringUtils.join(keys, ','));
                }
                else
                {
                    texMap.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            List<String> keys = Lists.newArrayListWithExpectedSize(width * height);
            Set<String> set = Sets.newHashSet(texMap.keySet());
            for (int i = 0; i < pattern.length; ++i)
            {
                for (int j = 0; j < pattern[i].length(); ++j)
                {
                    String s = pattern[i].substring(j, j + 1);
                    if (texMap.containsKey(s))
                    {
                        keys.add(texMap.get(s));
                        set.remove(s);
                    }
                    else
                    {
                        keys.add(null);
                    }
                }
            }
            if (!set.isEmpty())
            {
                throw new JsonSyntaxException("Key defines symbols that aren't used in pattern: " + set);
            }
            List<String> marks;
            if (JSONUtils.isJsonArray(json, "mark"))
            {
                JsonArray array = JSONUtils.getJsonArray(json, "mark");
                marks = Lists.newArrayListWithCapacity(array.size());
                array.forEach(e -> marks.add(e.getAsString()));
            }
            else
            {
                String mark = JSONUtils.getString(json, "mark", "");
                marks = Collections.singletonList(mark);
            }
            return new TextureBlockRecipe(recipeId, group, width, height, nonnulllist, itemstack, keys, marks);
        }

        @Override
        public TextureBlockRecipe read(ResourceLocation recipeId, PacketBuffer buffer)
        {
            int width = buffer.readVarInt();
            int height = buffer.readVarInt();
            String s = buffer.readString();
            NonNullList<Ingredient> nonnulllist = NonNullList.withSize(width * height, Ingredient.EMPTY);
            for (int k = 0; k < nonnulllist.size(); ++k)
            {
                nonnulllist.set(k, Ingredient.read(buffer));
            }
            ItemStack itemstack = buffer.readItemStack();

            List<String> keys = Lists.newArrayListWithExpectedSize(width * height);
            for (int i = 0; i < width * height; i++)
            {
                String k = buffer.readString();
                keys.add(k.isEmpty() ? null : k);
            }
            List<String> marks = ImmutableList.copyOf(buffer.readString().split(","));
            return new TextureBlockRecipe(recipeId, s, width, height, nonnulllist, itemstack, keys, marks);
        }

        @Override
        public void write(PacketBuffer buffer, TextureBlockRecipe recipe)
        {
            buffer.writeVarInt(recipe.getRecipeWidth());
            buffer.writeVarInt(recipe.getRecipeHeight());
            buffer.writeString(recipe.getGroup());
            for (Ingredient ingredient : recipe.getIngredients())
            {
                ingredient.write(buffer);
            }
            buffer.writeItemStack(recipe.getRecipeOutput());

            for (int i = 0; i < recipe.getRecipeWidth() * recipe.getRecipeHeight(); i++)
            {
                String k = recipe.textureKeys.get(i);
                buffer.writeString(k == null ? "" : k);
            }
            buffer.writeString(StringUtils.join(recipe.marks), ',');
        }
    }
}
