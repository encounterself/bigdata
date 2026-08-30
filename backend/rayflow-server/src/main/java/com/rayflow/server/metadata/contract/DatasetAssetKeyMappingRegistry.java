package com.rayflow.server.metadata.contract;

import com.rayflow.server.model.enums.MetadataAssetType;
import com.rayflow.server.model.enums.MetadataPlatform;

import java.util.Optional;

public interface DatasetAssetKeyMappingRegistry {

    Optional<String> resolve(DatasetIdentity dataset);

    record DatasetIdentity(
            long tenantId,
            MetadataPlatform platform,
            String namespace,
            String name,
            MetadataAssetType assetType
    ) {
        public DatasetIdentity {
            if (assetType != MetadataAssetType.TABLE && assetType != MetadataAssetType.FILESET) {
                throw new IllegalArgumentException("dataset mapping requires TABLE or FILESET asset type");
            }
        }

        public String canonicalAssetKey() {
            return AssetKeySpec.canonical(tenantId, platform.name(), namespace, name);
        }
    }
}
