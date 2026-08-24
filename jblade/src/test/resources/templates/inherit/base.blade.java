<html>
<head><title>@yield('title', 'DefaultTitle')</title></head>
<body>
<div class="sidebar">
@section('sidebar')
BASE
@show
</div>
<div class="content">@yield('content')</div>
</body>
</html>
