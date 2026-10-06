import discord
from discord import app_commands
from discord.ext import commands
import aiohttp
import os
import io
import qrcode
import asyncio
import datetime

TOKEN = "YOUR_DISCORD_BOT_TOKEN_HERE"

# ======== 服务器与角色 ========
GUILD_ID = 1555108404706418798          # 只在这个服务器生效
PAY_ROLE_ID = 1555189936058007563       # 购买后给的角色
LOG_CHANNEL_ID = 1555190151796363344    # 日志频道
# ==============================

# ======== 新 HTTPS 路径 ========
PAYMENT_API = 'https://pw.5w.pw/onyx_build/pay/api.php'
REGISTER_API = 'https://pw.5w.pw/onyx_build/zhuce.php'
# ==============================

REGISTER_PASSWORD = 'mmdjknb'
EXEMPT_ROLE_ID = 0
FORBIDDEN_KEYWORDS = ["http://", "discord.gg", "discord", "高润龙", "萌面雕", "高润", "龙", "凯"]

intents = discord.Intents.default()
intents.members = True
intents.message_content = True
bot = commands.Bot(command_prefix="!", intents=intents)

def load_purchased_users():
    if not os.path.exists('purchased_users.txt'):
        return set()
    with open('purchased_users.txt', 'r') as f:
        return set(line.strip() for line in f if line.strip())

def save_purchased_user(user_id):
    with open('purchased_users.txt', 'a') as f:
        f.write(str(user_id) + '\n')

def is_user_purchased(user_id):
    return str(user_id) in load_purchased_users()

def save_order_owner(trade_no, user_id):
    with open('order_owner.txt', 'a') as f:
        f.write(f"{trade_no}:{user_id}\n")

def get_order_owner(trade_no):
    if not os.path.exists('order_owner.txt'):
        return None
    with open('order_owner.txt', 'r') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split(':', 1)
            if len(parts) == 2 and parts[0] == trade_no:
                return parts[1]
    return None

def load_registered_users():
    if not os.path.exists('registered_users.txt'):
        return set()
    with open('registered_users.txt', 'r') as f:
        return set(line.strip() for line in f if line.strip())

def save_registered_user(user_id):
    with open('registered_users.txt', 'a') as f:
        f.write(str(user_id) + '\n')

def is_user_registered(user_id):
    return str(user_id) in load_registered_users()

def write_to_data_file(dc_id, username, password):
    with open('数据.txt', 'a', encoding='utf-8') as f:
        f.write(f"{dc_id} {username} {password} {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")

async def register_via_api(username, password, dc_id):
    url = f"{REGISTER_API}?password={REGISTER_PASSWORD}&username={username}&userpass={password}&dc_id={dc_id}"
    async with aiohttp.ClientSession() as session:
        try:
            async with session.get(url, timeout=aiohttp.ClientTimeout(total=10)) as resp:
                result = await resp.json()
                if result.get('status') == 'success':
                    return True, result.get('data', {}).get('message', '注册成功')
                return False, result.get('message', '注册失败')
        except asyncio.TimeoutError:
            return False, "注册接口超时"
        except Exception as e:
            return False, f"网络错误: {str(e)}"

def has_pay_role(interaction):
    guild = interaction.guild
    if not guild:
        return False
    pay_role = guild.get_role(PAY_ROLE_ID)
    if not pay_role:
        return False
    return pay_role in interaction.user.roles

class RegisterModal(discord.ui.Modal, title='注册账号'):
    username = discord.ui.TextInput(label='账号', placeholder='请输入您要注册的账号', required=True, min_length=3, max_length=32)
    password = discord.ui.TextInput(label='密码', placeholder='请输入密码', required=True, min_length=4, max_length=32)

    async def on_submit(self, interaction: discord.Interaction):
        await interaction.response.defer(ephemeral=True)
        if is_user_registered(interaction.user.id):
            await interaction.followup.send("您的DC账号已注册过，无法重复注册", ephemeral=True)
            return
        if not has_pay_role(interaction):
            await interaction.followup.send("您没有购买身份组，无法注册", ephemeral=True)
            return
        success, msg = await register_via_api(self.username.value.strip(), self.password.value.strip(), interaction.user.id)
        if success:
            save_registered_user(interaction.user.id)
            write_to_data_file(interaction.user.id, self.username.value.strip(), self.password.value.strip())
            await interaction.followup.send(
                f"注册成功\n账号: {self.username.value.strip()}\n授权时间: {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
                ephemeral=True
            )
            log_channel = bot.get_channel(LOG_CHANNEL_ID)
            if log_channel:
                await log_channel.send(f"新用户注册: {interaction.user} | 账号: {self.username.value.strip()}")
        else:
            await interaction.followup.send(f"注册失败: {msg}", ephemeral=True)

class RegisterButtonView(discord.ui.View):
    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="注册账号", style=discord.ButtonStyle.success)
    async def register_button(self, interaction: discord.Interaction, button: discord.ui.Button):
        if is_user_registered(interaction.user.id):
            await interaction.response.send_message("您的DC账号已注册过", ephemeral=True)
            return
        if not has_pay_role(interaction):
            await interaction.response.send_message("您没有购买身份组，无法注册", ephemeral=True)
            return
        await interaction.response.send_modal(RegisterModal())

class BuyServiceView(discord.ui.View):
    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="购买服务", style=discord.ButtonStyle.primary)
    async def buy_button(self, interaction: discord.Interaction, button: discord.ui.Button):
        if not interaction.guild or interaction.guild.id != GUILD_ID:
            await interaction.response.send_message("此操作只能在本服务器使用。", ephemeral=True)
            return
        await interaction.response.defer()
        try:
            user_id = interaction.user.id
            if is_user_purchased(user_id):
                await interaction.followup.send("您已购买过服务", ephemeral=True)
                return
            async with aiohttp.ClientSession() as session:
                async with session.post(f"{PAYMENT_API}?action=create", data={'user_id': str(user_id)}, timeout=aiohttp.ClientTimeout(total=15)) as resp:
                    result = await resp.json()
            if result.get('code') != 0:
                await interaction.followup.send(f"创建订单失败: {result.get('msg')}", ephemeral=True)
                return
            trade_no = result['trade_no']
            save_order_owner(trade_no, str(user_id))
            qr = qrcode.QRCode(version=1, error_correction=qrcode.constants.ERROR_CORRECT_L, box_size=10, border=4)
            qr.add_data(result['qrcode_url'])
            qr.make(fit=True)
            img = qr.make_image(fill_color="black", back_color="white")
            img_bytes = io.BytesIO()
            img.save(img_bytes, format='PNG')
            img_bytes.seek(0)
            embed = discord.Embed(title="订单已创建", description=f"订单号: {trade_no}\n请扫码支付", color=discord.Color.green())
            embed.set_image(url="attachment://qrcode.png")
            view = discord.ui.View()
            view.add_item(discord.ui.Button(label="购买服务", style=discord.ButtonStyle.primary, custom_id="buy_service", disabled=True))
            view.add_item(discord.ui.Button(label="官方频道", style=discord.ButtonStyle.link, url="https://baidu.com"))
            await interaction.followup.send(embed=embed, file=discord.File(img_bytes, filename='qrcode.png'), view=view)
            await interaction.delete_original_response()
            await interaction.followup.send(f"付款成功后发送 /验证订单号 {trade_no} 验证", ephemeral=False)
        except asyncio.TimeoutError:
            await interaction.followup.send("创建订单超时，请稍后再试", ephemeral=True)
        except Exception as e:
            await interaction.followup.send(f"处理订单时发生错误: {str(e)}", ephemeral=True)

@bot.tree.command(name="绑定用户", description="购买服务并注册账号")
async def bind_user(interaction: discord.Interaction):
    if not interaction.guild or interaction.guild.id != GUILD_ID:
        await interaction.response.send_message("此命令只能在本服务器使用。", ephemeral=True)
        return
    user_id = interaction.user.id
    if is_user_purchased(user_id):
        if is_user_registered(user_id):
            await interaction.response.send_message("您已购买并注册账号，可直接使用", ephemeral=True)
            return
        embed = discord.Embed(title="您已购买服务", description="请点击下方按钮注册账号", color=discord.Color.green())
        await interaction.response.send_message(embed=embed, view=RegisterButtonView(), ephemeral=True)
        return
    embed = discord.Embed(title="购买服务", description="您尚未购买该服务\n点击下方按钮购买，购买后即可注册账号", color=discord.Color.red())
    await interaction.response.send_message(embed=embed, view=BuyServiceView(), ephemeral=True)

@bot.tree.command(name="验证订单号", description="验证支付状态")
@app_commands.describe(order_no="订单号")
async def verify_order(interaction: discord.Interaction, order_no: str):
    if not interaction.guild or interaction.guild.id != GUILD_ID:
        await interaction.response.send_message("此命令只能在本服务器使用。", ephemeral=True)
        return
    await interaction.response.defer()
    try:
        user_id = interaction.user.id
        owner = get_order_owner(order_no)
        if not owner:
            await interaction.followup.send("该订单不存在或已过期")
            return
        if owner != str(user_id):
            await interaction.followup.send("订单并非您创建，验证失败")
            return
        async with aiohttp.ClientSession() as session:
            try:
                async with session.get(
                    f"{PAYMENT_API}?action=query&trade_no={order_no}",
                    timeout=aiohttp.ClientTimeout(total=10)
                ) as resp:
                    result = await resp.json()
            except asyncio.TimeoutError:
                await interaction.followup.send("查询超时，请稍后再试")
                return
            except Exception as e:
                await interaction.followup.send(f"请求支付接口失败: {str(e)}")
                return
        if result.get('code') != 0:
            await interaction.followup.send(f"查询失败: {result.get('msg')}")
            return
        if result.get('status') == 1:
            if not is_user_purchased(user_id):
                save_purchased_user(user_id)
            guild = interaction.guild
            role = guild.get_role(PAY_ROLE_ID)
            if role and role not in interaction.user.roles:
                try:
                    await interaction.user.add_roles(role, reason="支付成功")
                except discord.Forbidden:
                    await interaction.followup.send("机器人没有权限添加身份组，请检查权限设置")
                    return
            embed = discord.Embed(title="支付成功", description="请点击下方按钮注册账号", color=discord.Color.green())
            await interaction.followup.send(embed=embed, view=RegisterButtonView())
            log_channel = bot.get_channel(LOG_CHANNEL_ID)
            if log_channel:
                await log_channel.send(f"支付成功: {interaction.user} (ID: {user_id}) | 订单号: {order_no}")
        else:
            await interaction.followup.send("该订单尚未支付，请完成支付后再验证")
    except Exception as e:
        await interaction.followup.send(f"验证订单时发生错误: {str(e)}")

@bot.event
async def on_message(message):
    if message.author == bot.user:
        return
    if not message.guild:
        return
    if message.guild.id != GUILD_ID:
        return
    member = message.author
    if EXEMPT_ROLE_ID != 0 and any(role.id == EXEMPT_ROLE_ID for role in member.roles):
        await bot.process_commands(message)
        return
    content = message.content
    if any(kw in content for kw in FORBIDDEN_KEYWORDS):
        try:
            await message.delete()
            await member.timeout(discord.utils.utcnow() + datetime.timedelta(minutes=1), reason="发送违规关键词")
            await member.send("请勿发送违规词语，您已被禁言一分钟")
            log_channel = bot.get_channel(LOG_CHANNEL_ID)
            if log_channel:
                await log_channel.send(f"<@{member.id}> 因为发送违规词已被禁言")
        except Exception as e:
            print(f"违禁词处理失败: {e}")
        return
    await bot.process_commands(message)

@bot.event
async def on_ready():
    print(f"已登录为 {bot.user}")

    # ======== 正在玩 xxx 的状态显示 ========
    # 把 "Onyx_build 授权服务" 改成你想显示的文字
    await bot.change_presence(
        activity=discord.Activity(
            type=discord.ActivityType.playing,
            name="Onyx_build 授权服务"
        )
    )
    # ======================================

    for f in ['purchased_users.txt', 'registered_users.txt', 'order_owner.txt']:
        if not os.path.exists(f):
            with open(f, 'w') as fp:
                fp.write('')
    try:
        synced = await bot.tree.sync()
        print(f"已同步 {len(synced)} 条全局斜杠命令")
    except Exception as e:
        print(f"同步失败: {e}")

if __name__ == "__main__":
    bot.run(TOKEN)