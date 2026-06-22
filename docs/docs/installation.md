# Installation

## Version compatibility

Elasticsearch plugins are built against a specific Elasticsearch version and load only on a matching
cluster. Use a Search Redact build that matches your cluster's Elasticsearch version.

## Build the plugin

From a clone of the repository:

```
./gradlew build
```

The plugin zip is written to `build/distributions/search-redact-<version>.zip`.

## Install into Elasticsearch

Install the zip on every node, then restart the node:

```
bin/elasticsearch-plugin install --batch file:/path/to/search-redact-<version>.zip
```

## Verify

Confirm the plugin is loaded:

```
GET _cat/plugins?v
```

The `search-redact` plugin should appear in the list.

## Remove

```
bin/elasticsearch-plugin remove search-redact
```

Restart the node after removing the plugin.
