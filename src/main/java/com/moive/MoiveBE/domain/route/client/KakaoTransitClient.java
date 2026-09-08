package com.moive.MoiveBE.domain.route.client;

import com.moive.MoiveBE.domain.route.dto.KakaoTransitRouteResponse;
import com.moive.MoiveBE.domain.route.dto.Location;

public interface KakaoTransitClient {

    KakaoTransitRouteResponse getTransitRoute(Location start, Location end);
}
