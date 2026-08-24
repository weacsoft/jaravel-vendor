@if ($paginator->hasPages())
<ul class="pager">
    @foreach ($elements as $element)
        @if ($element['type'] == 'separator')
            <li class="sep">...</li>
        @else
            @if ($element['active'])
                <li class="active">{{ $element['page'] }}</li>
            @else
                <li><a href="{{ $element['url'] }}">{{ $element['page'] }}</a></li>
            @endif
        @endif
    @endforeach
</ul>
@endif
