Introduction
------------
该库提供令牌桶算法的实现，该算法对于提供对一部分代码的限速访问是有用的。 所提供的实现是在桶的容量有限的意义上的“泄漏桶”的实现，并且超过该容量的任何添加的令牌将“溢出”到桶中并永远丢失。
在这个实现中，重新填充桶的规则被封装在提供的RefillStrategy实例中。 在尝试使用任何令牌之前，将咨询充值策略，以了解应该将多少个令牌添加到存储桶中

使用 [Travis CI](http://about.travis-ci.org) 进行持续集成  [![Build Status](https://secure.travis-ci.org/bbeck/token-bucket.png?branch=master)](http://travis-ci.org/bbeck/token-bucket)

参考:

* [Wikipedia - Token Bucket](http://en.wikipedia.org/wiki/Token_bucket)
* [Wikipedia - Leaky Bucket](http://en.wikipedia.org/wiki/Leaky_bucket)

使用
-----
使用令牌桶是非常容易的，最好通过一个例子来说明。 假设您有一段代码可以轮询网站，而您只希望能够每秒访问一次站点：

```java
// 创建容量为1个令牌的令牌桶，以1个令牌/秒的固定间隔进行补充。
TokenBucket bucket = TokenBuckets.builder()
  .withCapacity(1)
  .withFixedIntervalRefillStrategy(1, 1, TimeUnit.SECONDS)
  .build();

// ...

while (true) {
  //从令牌桶中消耗令牌。 如果令牌不可用，则该方法将阻塞，直到补充策略向桶中添加一个。
  bucket.consume(1);

  poll();
}
```

另一个例子假设您想要将服务器对客户端的大小响应限制为20 kb /秒，但是希望允许40 kb /秒的周期性突发速率：

```java
// 创建容量为40 kb的令牌桶，以每秒20 kb令牌的固定间隔补充
TokenBucket bucket = TokenBuckets.builder()
  .withCapacity(40960)
  .withFixedIntervalRefillStrategy(20480, 1, TimeUnit.SECONDS)
  .build();

// ...

while (true) {
  String response = prepareResponse();

  // 从桶中消耗令牌，与响应的大小相称
  bucket.consume(response.length());

  send(response);
}
```

Maven Setup
-----------
令牌桶库通过maven中心分发。 只要把它作为你的依赖 ```pom.xml```.

```xml
<dependency>
    <groupId>org.isomorphism</groupId>
    <artifactId>token-bucket</artifactId>
    <version>1.6</version>
</dependency>
```

License
-------
Copyright 2012-2015 Brandon Beck
Licensed under the Apache Software License, Version 2.0: <http://www.apache.org/licenses/LICENSE-2.0>.
