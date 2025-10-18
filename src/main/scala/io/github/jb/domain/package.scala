package io.github.jb

package object domain {
  // JSON codecs

  import com.github.plokhotnyuk.jsoniter_scala.macros.*
  import com.github.plokhotnyuk.jsoniter_scala.core.*

  given JsonValueCodec[UserCreate] = JsonCodecMaker.make

  given JsonValueCodec[UserResponse] = JsonCodecMaker.make

  given JsonValueCodec[List[UserResponse]] = JsonCodecMaker.make

  given JsonValueCodec[UserStatusUpdate] = JsonCodecMaker.make

  given JsonValueCodec[LoginRequest] = JsonCodecMaker.make

  given JsonValueCodec[Tokens] = JsonCodecMaker.make

  given JsonValueCodec[AuthResponse] = JsonCodecMaker.make

  given JsonValueCodec[AccessTokenClaims] = JsonCodecMaker.make

  given JsonValueCodec[RefreshTokenClaims] = JsonCodecMaker.make

  given JsonValueCodec[Boolean] = JsonCodecMaker.make

}
