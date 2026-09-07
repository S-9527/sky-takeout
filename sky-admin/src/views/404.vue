<template>
  <div class="wscn-http404-container">
    <div class="wscn-http404">
      <div class="pic-404">
        <img
          class="pic-404__parent"
          src="@/assets/404-images/404.png"
          alt="404"
        >
        <img
          class="pic-404__child left"
          src="@/assets/404-images/404-cloud.png"
          alt="404"
        >
        <img
          class="pic-404__child mid"
          src="@/assets/404-images/404-cloud.png"
          alt="404"
        >
        <img
          class="pic-404__child right"
          src="@/assets/404-images/404-cloud.png"
          alt="404"
        >
      </div>
      <div class="text-404">
        <div class="text-404__oops">
          OOPS!
        </div>
        <div class="text-404__info">
          All rights reserved
          <a
            style="color:#20a0ff"
            href="https://wallstreetcn.com"
            target="_blank"
          >wallstreetcn</a>
        </div>
        <div class="text-404__headline">
          {{ message }}
        </div>
        <div class="text-404__info">
          Please check that the URL you entered is correct, or click the button below to return to the homepage.
        </div>
        <a
          href=""
          class="text-404__return-home"
        >Back to home</a>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const message = '404 Page Not Found'
</script>