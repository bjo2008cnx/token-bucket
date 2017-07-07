--[[
实现一个访问频率控制，某个ip在短时间内频繁访问页面，需要记录并检测出来，就可以通过Lua脚本高效的实现
在redis客户端机器上，如何测试这个脚本呢？如下：
redis-cli --eval ratelimit.lua rate.limitingl:127.0.0.1 , 10 3
--eval参数是告诉redis-cli读取并运行后面的Lua脚本，ratelimiting.lua是脚本的位置，后面跟着是传给Lua脚本的参数。其中","前的rate.limiting:127.0.0.1是要操作的键，可以再脚本中用KEYS[1]获取，","后面的10和3是参数，在脚本中能够使用ARGV[1]和ARGV[2]获得。注：","两边的空格不能省略，否则会出错
结合脚本的内容可知这行命令的作用是将访问频率限制为每10秒最多3次，所以在终端中不断的运行此命令会发现当访问频率在10秒内小于或等于3次时返回1，否则返回0。
--]]

local times = redis.call('incr',KEYS[1])

if times == 1 then
    redis.call('expire',KEYS[1], ARGV[1])
end

if times > tonumber(ARGV[2]) then
    return 0
end
return 1



