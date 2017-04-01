# GL 2D Watch Face

## What is it? 
A light-weight library for rendering watch faces in OpenGL, with support for Bitmap/Canvas. Plus, a cool example watch face! 

![example watch face](https://www.reddit.com/r/AndroidWear/comments/62m7te/dev_my_lightweight_library_for_rendering_watch/?st=J0YOOFWD&sh=0cd14f5a)

## Why use it? 
Watch faces that demand very performant or complex rendering can leverage the power of the entire graphics pipeline without sacrificing the simplicity of working with Bitmaps and the Canvas API. Have power when you need it -- e.g. custom shaders to modify a Bitmap, GPU-accelerated transformations, access to more texture formats, etc.

## Acknowledgements
Majority of the credit for the example watchface goes to the author of the ring shader, Phil, which was posted on shadertoy here https://www.shadertoy.com/view/ltBXRc (these details are at the top of the fragment shader).

## License
This software is licensed under the following:
```
The MIT License (MIT)

Copyright (c) 2015 ustwo studio inc (www.ustwo.com)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 ```
 
 Shader for the example watch face (circle_frag.glsl) is licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported license (CC BY-NC-SA 3.0). https://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_US
