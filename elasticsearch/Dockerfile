# 带 IK 中文分词器的 Elasticsearch 镜像
# 商品搜索索引依赖 ik_max_word / ik_smart 分词器，原生镜像不含该插件，
# 缺失时 product-search-service 的 putMapping 会失败并触发启动 fail-fast。
ARG ES_VERSION=9.2.3
FROM docker.elastic.co/elasticsearch/elasticsearch:${ES_VERSION}

# IK 插件版本必须与 ES 版本严格一致；如下载地址变更可通过 build arg 覆盖
ARG IK_ZIP_URL=https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-9.2.3.zip
RUN elasticsearch-plugin install --batch ${IK_ZIP_URL}
