/**
 *    Copyright 2009-2021 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.scripting.xmltags;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration;
  // 这个rootSqlNode使用了责任链 + 装饰的模式，里面包装了一层又一层的SqlNode
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  /**
   * 这个方法主要做2件事：
   * 1. 解析所有sqlNode  解析成一条完整sql语句
   * 2. 将sql语句中的#{} 替换成问号， 并且把#{}中的参数解析成ParameterMapping （里面包含了typeHandler)
   * @param parameterObject: 参数对象，实际调用的时候传递进来的，一些Collection/Array —> 包装成Map
   * @return
   */
  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    /**
     * <MixedSqlNode>
     *   <StaticTestSqlNode/>
     *   <TrimSqlNode>
     *    <MixedSqlNode>
     *      <IfSqlNode>
     *        <MixedSqlNode>
     *          <StaticTestSqlNode/>
     *        <MixedSqlNode/>
     *      <IfSqlNode/>
     *    <MixedSqlNode/>
     *   <TrimSqlNode/>
     *   <StaticTestSqlNode/>
     * <MixedSqlNode/>
     */
    // 1归 责任链 处理一个个SqlNode   编译出一个完整sql
    // 递归：先执行外层SqlNode的再执行里面的SqlNode，一层一层这样来执行的
    // 最终的sql会拼接到context的StringJoiner中
    rootSqlNode.apply(context);
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    // 2.接下来处理 处理sql中的#{...}
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    // 怎么处理呢？ 很简单， 就是拿到#{}中的内容 封装为parameterMapper，  替换成?
    // 返回的时StaticSqlSource
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    context.getBindings().forEach(boundSql::setAdditionalParameter);
    return boundSql;
  }

}
