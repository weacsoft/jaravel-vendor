@component('comp-alert', ['type' => 'warning', 'title' => '注意'])
这是提示内容
@endcomponent

@component('comp-card')
@slot('header')
卡片标题
@endslot
卡片主体
@slot('footer')
卡片脚注
@endslot
@endcomponent

@foreach ($users as $u)
USER:{{ $u['name'] }}-{{ $u['age'] }};
@endforeach
