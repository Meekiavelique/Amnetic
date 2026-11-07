package com.meekdev.amnetic.client.content;

import com.meekdev.amnetic.client.content.font.FontCheckRegression;
import com.meekdev.amnetic.client.model.internal.ammesh.AmmeshReaderRegression;
import com.meekdev.amnetic.client.model.internal.ammesh.TextureCacheRegression;
import com.meekdev.amnetic.client.model.internal.parse.BbmodelInflateRegression;
import com.meekdev.amnetic.client.model.internal.parse.GltfPreflightRegression;

// every decoder amnetic runs on a game's files, each fed hostile samples
public final class HostileContentRegression {
    public static void main(String[] args) throws Exception {
        ImageProbeRegression.verify();
        TextureCacheRegression.verify();
        AmmeshReaderRegression.verify();
        GltfPreflightRegression.verify();
        BbmodelInflateRegression.verify();
        FontCheckRegression.verify();
        System.out.println("PASS: hostile content regressions");
    }
}
